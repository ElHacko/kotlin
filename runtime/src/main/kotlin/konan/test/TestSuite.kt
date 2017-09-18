package konan.test

interface TestCase {
    val name: String
    val suite: TestSuite
}

interface TestSuite {
    val name: String
    val testCases: Map<String, TestCase>
    fun run(listener: TestListener)
}

enum class TestFunctionKind {
    BEFORE_EACH,
    AFTER_EACH,
    BEFORE_CLASS,
    AFTER_CLASS
}

abstract class AbstractTestSuite<F: Function<Unit>>(override val name: String): TestSuite {
    override fun toString(): String = name

    // TODO: Make inner and remove the type param when the bug is fixed.
    class BasicTestCase<F: Function<Unit>>(
            override val name: String,
            override val suite: AbstractTestSuite<F>,
            val testFunction: F
    ) : TestCase {
        override fun toString(): String = "$name ($suite)"
    }

    private val _testCases = mutableMapOf<String, BasicTestCase<F>>()
    override val testCases: Map<String, BasicTestCase<F>>
        get() = _testCases

    private fun registerTestCase(testCase: BasicTestCase<F>) = _testCases.put(testCase.name, testCase)
    fun registerTestCase(name: String, testFunction: F) = registerTestCase(createTestCase(name, testFunction))
    fun createTestCase(name: String, testFunction: F) = BasicTestCase(name, this, testFunction)

    protected abstract fun doBeforeClass()
    protected abstract fun doAfterClass()

    protected abstract fun doTest(testCase: BasicTestCase<F>)

    init {
        TestRunner.register(this)
    }

    override fun run(listener: TestListener) {
        doBeforeClass()
        testCases.values.forEach {
            try {
                listener.start(it)
                doTest(it)
                listener.pass(it)
            } catch (e: Throwable) {
                listener.fail(it, e)
            }

        }
        doAfterClass()
    }
}

abstract class BaseClassSuite<INSTANCE, COMPANION>(name: String): AbstractTestSuite<INSTANCE.() -> Unit>(name) {

    // These two methods are overrided in test suite classes generated by the compiler.
    abstract fun createInstance(): INSTANCE
    open fun getCompanion(): COMPANION = throw NotImplementedError("Test class has no companion object")

    companion object {
        val INSTANCE_KINDS = listOf(TestFunctionKind.BEFORE_EACH, TestFunctionKind.AFTER_EACH)
        val COMPANION_KINDS = listOf(TestFunctionKind.BEFORE_CLASS, TestFunctionKind.AFTER_CLASS)
    }

    private val instanceFunctions = mutableMapOf<TestFunctionKind, MutableSet<INSTANCE.() -> Unit>>()
    private fun getInstanceFunctions(kind: TestFunctionKind): MutableCollection<INSTANCE.() -> Unit> {
        check(kind in INSTANCE_KINDS)
        return instanceFunctions.getOrPut(kind) { mutableSetOf() }
    }

    private val companionFunction = mutableMapOf<TestFunctionKind, MutableSet<COMPANION.() -> Unit>>()
    private fun getCompanionFunctions(kind: TestFunctionKind): MutableCollection<COMPANION.() -> Unit> {
        check(kind in COMPANION_KINDS)
        return companionFunction.getOrPut(kind) { mutableSetOf() }
    }

    val before:      Collection<INSTANCE.() -> Unit>  get() = getInstanceFunctions(TestFunctionKind.BEFORE_EACH)
    val after:       Collection<INSTANCE.() -> Unit>  get() = getInstanceFunctions(TestFunctionKind.AFTER_EACH)

    val beforeClass: Collection<COMPANION.() -> Unit>  get() = getCompanionFunctions(TestFunctionKind.BEFORE_CLASS)
    val afterClass:  Collection<COMPANION.() -> Unit>  get() = getCompanionFunctions(TestFunctionKind.AFTER_CLASS)

    @Suppress("UNCHECKED_CAST")
    fun registerFunction(kind: TestFunctionKind, function: Function1<*, Unit>) =
            when (kind) {
                in INSTANCE_KINDS -> getInstanceFunctions(kind).add(function as INSTANCE.() -> Unit)
                in COMPANION_KINDS -> getCompanionFunctions(kind).add(function as COMPANION.() -> Unit)
                else -> throw IllegalArgumentException("Unknown function kind: $kind")
            }

    override fun doBeforeClass() = beforeClass.forEach { getCompanion().it() }
    override fun doAfterClass() = afterClass.forEach { getCompanion().it() }

    override fun doTest(testCase: BasicTestCase<INSTANCE.() -> Unit>) {
        val instance = createInstance()
        val testFunction = testCase.testFunction
        try {
            before.forEach { instance.it() }
            instance.testFunction()
        } finally {
            after.forEach { instance.it() }
        }
    }
}

private typealias TopLevelFun = () -> Unit

class TopLevelSuite(name: String): AbstractTestSuite<TopLevelFun>(name) {

    private val specialFunctions = mutableMapOf<TestFunctionKind, MutableSet<TopLevelFun>>()
    private fun getFunctions(type: TestFunctionKind) = specialFunctions.getOrPut(type) { mutableSetOf() }

    val before:      Collection<TopLevelFun>  get() = getFunctions(TestFunctionKind.BEFORE_EACH)
    val after:       Collection<TopLevelFun>  get() = getFunctions(TestFunctionKind.AFTER_EACH)
    val beforeClass: Collection<TopLevelFun>  get() = getFunctions(TestFunctionKind.BEFORE_CLASS)
    val afterClass:  Collection<TopLevelFun>  get() = getFunctions(TestFunctionKind.AFTER_CLASS)

    fun registerFunction(kind: TestFunctionKind, function: TopLevelFun) = getFunctions(kind).add(function)

    override fun doBeforeClass() = beforeClass.forEach { it() }
    override fun doAfterClass() = afterClass.forEach { it() }

    override fun doTest(testCase: BasicTestCase<() -> Unit>) =
            try {
                before.forEach { it() }
                testCase.testFunction()
            } finally {
                after.forEach { it() }
            }
}