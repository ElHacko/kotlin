// DONT_TARGET_EXACT_BACKEND: WASM
// WASM_MUTE_REASON: STDLIB_STRING_BUILDER
// KJS_WITH_FULL_RUNTIME
class Outer(val foo: StringBuilder) {
  inner class Inner() {
    fun len() : Int {
      return foo.length
    }
  }

  fun test() : Inner {
    return Inner()
  }
}

fun box() : String {
  val sb = StringBuilder("xyzzy")
  val o = Outer(sb)
  val i = o.test()
  val l = i.len()
  return if (l != 5) "fail" else "OK"
}
