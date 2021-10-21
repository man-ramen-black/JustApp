package com.black.study.justcode

class DecompileSample {
    /**
     * var를 decompile하면?
     */
    /*
     private int a = 5;
     public final int getA() { return a; }
     public final void setA(int var) { a = var; }
     */
    var a = 5

    /**
     * val를 decompile하면?
     */
    /*
     private final int b = 4;
     public final int getA() { return a; }
     */
    val b = 4

    /**
     * 확장 함수를 decompile하면?
     */
    /*
     public final void test(@NotNull String $this$test) {
         Intrinsics.checkNotNullParameter($this$test, "$this$test");
         boolean var2 = false;
         System.out.println($this$test);
     }
     */
    fun String.test() {
        println(this)
    }
}