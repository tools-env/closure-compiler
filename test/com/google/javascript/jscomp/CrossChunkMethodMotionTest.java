/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CrossChunkMethodMotion}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class CrossChunkMethodMotionTest extends CompilerTestCase {
  private static final String EXTERNS =
      "IFoo.prototype.bar; var mExtern; mExtern.bExtern; mExtern['cExtern'];";

  private boolean canMoveExterns = false;
  private boolean noStubs = false;
  private static final String STUB_DECLARATIONS = CrossChunkMethodMotion.STUB_DECLARATIONS;

  public CrossChunkMethodMotionTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CrossChunkMethodMotion(compiler, new IdGenerator(), canMoveExterns, noStubs);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    canMoveExterns = false;
    noStubs = false;
    enableNormalize();
  }

  @Test
  public void testMovePrototypeMethod1() {
    testSame(createModuleChain(
                 "function Foo() {}" +
                 "Foo.prototype.bar = function() {};",
                 // Module 2
                 "(new Foo).bar()"));

    canMoveExterns = true;
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.bar = function() {};",
             // Module 2
             "(new Foo).bar()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.bar = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.bar = JSCompiler_unstubMethod(0, function() {});" +
             "(new Foo).bar()"
         });
  }

  @Test
  public void testMovePrototypeMethod2() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype = { method: function() {} };",
             // Module 2
             "(new Foo).method()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype = { method: JSCompiler_stubMethod(0) };",
             // Module 2
             "Foo.prototype.method = " +
             "    JSCompiler_unstubMethod(0, function() {});" +
             "(new Foo).method()"
         });
  }

  @Test
  public void testMovePrototypeMethod3() {
    testSame(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype = { get method() {} };",
             // Module 2
             "(new Foo).method()"));
  }

  @Test
  public void testMovePrototypeMethodWithoutStub() {
    testSame(createModuleChain(
        "function Foo() {}" +
            "Foo.prototype.bar = function() {};",
        // Module 2
        "(new Foo).bar()"));

    canMoveExterns = true;
    noStubs = true;
    test(createModuleChain(
            "function Foo() {}" +
                "Foo.prototype.bar = function() {};",
            // Module 2
            "(new Foo).bar()"),
        new String[] {
                "function Foo() {}",
            // Module 2
            "Foo.prototype.bar = function() {};" +
                "(new Foo).bar()"
        });
  }

  @Test
  public void testNoMovePrototypeMethodIfAliasedNoStubs() {
    // don't move if noStubs enabled and there's a reference to the method to be moved
    noStubs = true;
    testSame(
        createModuleChain(
            "function Foo() {}"
                + "Foo.prototype.m = function() {};"
                + "Foo.prototype.m2 = Foo.prototype.m;",
            // Module 2
            "(new Foo).m()"));

    testSame(
        createModuleChain(
            "function Foo() {}"
                + "Foo.prototype.m = function() {};"
                + "Foo.prototype.m2 = Foo.prototype.m;",
            // Module 2
            "(new Foo).m(), (new Foo).m2()"));

    noStubs = false;

    test(
        createModuleChain(
            "function Foo() {}"
                + "Foo.prototype.m = function() {};"
                + "Foo.prototype.m2 = Foo.prototype.m;",
            // Module 2
            "(new Foo).m()"),
        new String[] {
          STUB_DECLARATIONS
              + "function Foo() {}"
              + "Foo.prototype.m = JSCompiler_stubMethod(0);"
              + "Foo.prototype.m2 = Foo.prototype.m;",
          // Module 2
          "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});" + "(new Foo).m()"
        });

    test(
        createModuleChain(
            "function Foo() {}"
                + "Foo.prototype.m = function() {};"
                + "Foo.prototype.m2 = Foo.prototype.m;",
            // Module 2
            "(new Foo).m(), (new Foo).m2()"),
        new String[] {
          STUB_DECLARATIONS
              + "function Foo() {}"
              + "Foo.prototype.m = JSCompiler_stubMethod(0);"
              + "Foo.prototype.m2 = Foo.prototype.m;",
          // Module 2
          "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});"
              + "(new Foo).m(), (new Foo).m2()",
        });
  }

  @Test
  public void testNoMovePrototypeMethodRedeclaration1() {
    // don't move if it can be overwritten when a sibling module is loaded.
    testSame(createModuleStar(
             "function Foo() {}" +
             "Foo.prototype.method = function() {};",
             // Module 2
             "Foo.prototype.method = function() {};",
             // Module 3
             "(new Foo).method()"));
  }

  @Test
  public void testNoMovePrototypeMethodRedeclaration2() {
    // don't move if it can be overwritten when a later module is loaded.
    testSame(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.method = function() {};",
             // Module 2
             "(new Foo).method()",
             // Module 3
             "Foo.prototype.method = function() {};"));
  }

  @Test
  public void testNoMovePrototypeMethodRedeclaration3() {
    // Note: it is reasonable to move the method in this case,
    // but it is difficult enough to prove that we don't.
    testSame(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.method = function() {};",
             // Module 2
             "Foo.prototype.method = function() {};",
             // Module 3
             "(new Foo).method()"));
  }

  @Test
  public void testMovePrototypeRecursiveMethod() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() { this.baz(); };",
             // Module 2
             "(new Foo).baz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, " +
             "    function() { this.baz(); });" +
             "(new Foo).baz()"
         });
  }

  @Test
  public void testCantMovePrototypeProp() {
    testSame(createModuleChain(
                 "function Foo() {}" +
                 "Foo.prototype.baz = goog.nullFunction;",
                 // Module 2
                 "(new Foo).baz()"));
  }

  @Test
  public void testMoveMethodsInRightOrder() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() { return 1; };" +
             "Foo.prototype.baz = function() { return 2; };",
             // Module 2
             "(new Foo).baz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(1);" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(1, function() { return 1; });" +
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(0, function() { return 2; });" +
             "(new Foo).baz()"
         });
  }

  @Test
  public void testMoveMethodsInRightOrder2() {
    JSModule[] m = createModules(
        "function Foo() {}" +
        "Foo.prototype.baz = function() { return 1; };" +
        "function Goo() {}" +
        "Goo.prototype.baz = function() { return 2; };",
        // Module 2, depends on 1
        "",
        // Module 3, depends on 2
        "(new Foo).baz()",
        // Module 4, depends on 3
        "",
        // Module 5, depends on 3
        "(new Goo).baz()");

    m[1].addDependency(m[0]);
    m[2].addDependency(m[1]);
    m[3].addDependency(m[2]);
    m[4].addDependency(m[2]);

    test(m,
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(1);" +
             "function Goo() {}" +
             "Goo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "",
             // Module 3
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(1, function() { return 1; });" +
             "Goo.prototype.baz = " +
             "JSCompiler_unstubMethod(0, function() { return 2; });" +
             "(new Foo).baz()",
             // Module 4
             "",
             // Module 5
             "(new Goo).baz()"
         });
  }

  @Test
  public void testMoveMethodsUsedInTwoModules() {
    testSame(createModuleStar(
                 "function Foo() {}" +
                 "Foo.prototype.baz = function() {};",
                 // Module 2
                 "(new Foo).baz()",
                 // Module 3
                 "(new Foo).baz()"));
  }

  @Test
  public void testMoveMethodsUsedInTwoModules2() {
    JSModule[] modules = createModules(
        "function Foo() {}" +
        "Foo.prototype.baz = function() {};",
        // Module 2
        "", // a blank module in the middle
        // Module 3
        "(new Foo).baz() + 1",
        // Module 4
        "(new Foo).baz() + 2");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);
    test(modules,
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
             // Module 3
             "(new Foo).baz() + 1",
             // Module 4
             "(new Foo).baz() + 2"
         });
  }

  @Test
  public void testTwoMethods() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() {};",
             // Module 2
             "Foo.prototype.callBaz = function() { this.baz(); }",
             // Module 3
             "(new Foo).callBaz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.callBaz = JSCompiler_stubMethod(1);",
             // Module 3
             "Foo.prototype.callBaz = " +
             "  JSCompiler_unstubMethod(1, function() { this.baz(); });" +
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});" +
             "(new Foo).callBaz()"
         });
  }

  @Test
  public void testTwoMethods2() {
    // if the programmer screws up the module order, we don't try to correct
    // the mistake.
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() {};",
             // Module 2
             "(new Foo).callBaz()",
             // Module 3
             "Foo.prototype.callBaz = function() { this.baz(); }"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "(new Foo).callBaz()",
             // Module 3
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});" +
             "Foo.prototype.callBaz = function() { this.baz(); };"
         });
  }

  @Test
  public void testGlobalFunctionsInGraph() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function() {};" +
            "function x() { return (new Foo).baz(); }",
            // Module 2
            "x();"),
        new String[] {
          STUB_DECLARATIONS +
          "function Foo() {}" +
          "Foo.prototype.baz = JSCompiler_stubMethod(0);" +
          "function x() { return (new Foo).baz(); }",
          // Module 2
          "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});" +
          "x();"
        });
  }

  // Read of closure variable disables method motions.
  @Test
  public void testClosureVariableReads1() {
    testSame(createModuleChain(
            "function Foo() {}" +
            "(function() {" +
            "var x = 'x';" +
            "Foo.prototype.baz = function() {x};" +
            "})();",
            // Module 2
            "var y = new Foo(); y.baz();"));
  }

  // Read of global variable is fine.
  @Test
  public void testClosureVariableReads2() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.b1 = function() {" +
            "  var x = 1;" +
            "  Foo.prototype.b2 = function() {" +
            "    Foo.prototype.b3 = function() {" +
            "      x;" +
            "    }" +
            "  }" +
            "};",
            // Module 2
            "var y = new Foo(); y.b1();",
            // Module 3
            "y = new Foo(); z.b2();",
            // Module 4
            "y = new Foo(); z.b3();"
            ),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.b1 = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {" +
           "  var x = 1;" +
           "  Foo.prototype.b2 = function() {" +
           "    Foo.prototype.b3 = function() {" +
           "      x;" +
           "    }" +
           "  }" +
           "});" +
           "var y = new Foo(); y.b1();",
           // Module 3
           "y = new Foo(); z.b2();",
           // Module 4
           "y = new Foo(); z.b3();"
        });
  }

  @Test
  public void testClosureVariableReads3() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.b1 = function() {" +
            "  Foo.prototype.b2 = function() {" +
            "    var x = 1;" +
            "    Foo.prototype.b3 = function() {" +
            "      x;" +
            "    }" +
            "  }" +
            "};",
            // Module 2
            "var y = new Foo(); y.b1();",
            // Module 3
            "y = new Foo(); z.b2();",
            // Module 4
            "y = new Foo(); z.b3();"
            ),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.b1 = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {" +
           "  Foo.prototype.b2 = JSCompiler_stubMethod(1);" +
           "});" +
           "var y = new Foo(); y.b1();",
           // Module 3
           "Foo.prototype.b2 = JSCompiler_unstubMethod(1, function() {" +
           "  var x = 1;" +
           "  Foo.prototype.b3 = function() {" +
           "    x;" +
           "  }" +
           "});" +
           "y = new Foo(); z.b2();",
           // Module 4
           "y = new Foo(); z.b3();"
        });
  }

  // Read of global variable is fine.
  @Test
  public void testNoClosureVariableReads1() {
    test(createModuleChain(
            "function Foo() {}" +
            "var x = 'x';" +
            "Foo.prototype.baz = function(){x};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "var x = 'x';" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(0, function(){x});" +
           "var y = new Foo(); y.baz();"
        });
  }

  // Read of a local is fine.
  @Test
  public void testNoClosureVariableReads2() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function(){var x = 1;x};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(" +
           "    0, function(){var x = 1; x});" +
           "var y = new Foo(); y.baz();"
        });
  }

  // An anonymous inner function reading a closure variable is fine.
  @Test
  public void testInnerFunctionClosureVariableReads() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function(){var x = 1;" +
            "  return function(){x}};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(" +
           "    0, function(){var x = 1; return function(){x}});" +
           "var y = new Foo(); y.baz();"
        });
  }

  @Test
  public void testIssue600() {
    testSame(
        createModuleChain(
            "var jQuery1 = (function() {\n" +
            "  var jQuery2 = function() {};\n" +
            "  var theLoneliestNumber = 1;\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return theLoneliestNumber;\n" +
            "    }\n" +
            "  };\n" +
            "  return jQuery2;\n" +
            "})();\n",

            "(function() {" +
            "  var div = jQuery1('div');" +
            "  div.size();" +
            "})();"));
  }

  @Test
  public void testIssue600b() {
    testSame(
        createModuleChain(
            "var jQuery1 = (function() {\n" +
            "  var jQuery2 = function() {};\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return 1;\n" +
            "    }\n" +
            "  };\n" +
            "  return jQuery2;\n" +
            "})();\n",

            "(function() {" +
            "  var div = jQuery1('div');" +
            "  div.size();" +
            "})();"));
  }

  @Test
  public void testIssue600c() {
    test(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "jQuery2.prototype = {\n" +
            "  size: function() {\n" +
            "    return 1;\n" +
            "  }\n" +
            "};\n",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"),
        new String[] {
            STUB_DECLARATIONS +
            "var jQuery2 = function() {};\n" +
            "jQuery2.prototype = {\n" +
            "  size: JSCompiler_stubMethod(0)\n" +
            "};\n",
            "jQuery2.prototype.size=" +
            "    JSCompiler_unstubMethod(0,function(){return 1});" +
            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"
        });
  }

  @Test
  public void testIssue600d() {
    test(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return 1;\n" +
            "    }\n" +
            "  };\n" +
            "})();",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"),
        new String[] {
            STUB_DECLARATIONS +
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  jQuery2.prototype = {\n" +
            "    size: JSCompiler_stubMethod(0)\n" +
            "  };\n" +
            "})();",
            "jQuery2.prototype.size=" +
            "    JSCompiler_unstubMethod(0,function(){return 1});" +
            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"
        });
  }

  @Test
  public void testIssue600e() {
    testSame(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  var theLoneliestNumber = 1;\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return theLoneliestNumber;\n" +
            "    }\n" +
            "  };\n" +
            "})();",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"));
  }

  @Test
  public void testPrototypeOfThisAssign() {
    testSame(
        createModuleChain(
            "/** @constructor */" +
            "function F() {}" +
            "this.prototype.foo = function() {};",
            "(new F()).foo();"));
  }

  @Test
  public void testDestructuring() {
    test(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype.foo = function() {};"),
            "const {foo} = new F();"),
        new String[] {
          STUB_DECLARATIONS
              + lines(
                  "/** @constructor */", //
                  "function F() {}",
                  "F.prototype.foo = JSCompiler_stubMethod(0);"),
          lines(
              "F.prototype.foo = JSCompiler_unstubMethod(0, function(){});", //
              "const {foo} = new F();")
        });
  }

  @Test
  public void testDestructuringWithQuotedProp() {
    testSame(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype.foo = function() {};"),
            "const {'foo': foo} = new F();"));
  }

  @Test
  public void testDestructuringWithComputedProp() {
    // See https://github.com/google/closure-compiler/issues/3145
    testSame(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype['foo'] = function() {};"),
            "const {['foo']: foo} = new F();"));
  }
}