/*
 * Created on Sep 13, 2004
 *
 * @author Fabio Zadrozny
 */
package org.python.pydev.editor.codecompletion;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import junit.framework.TestCase;

/**
 * @author Fabio Zadrozny
 */
public class PythonShellTest extends TestCase {

    private PythonShell shell;

    public static void main(String[] args) {
        junit.textui.TestRunner.run(PythonShellTest.class);
    }

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        File f = new File("PySrc/pycompletionserver.py");
        shell = new PythonShell(f);

        shell.startIt();
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
        shell.endIt();
    }

    public void testGetGlobalCompletions() throws IOException, CoreException {
        String str = "import math\n";
        List list = shell.getGlobalCompletions(str);
        for (Iterator iter = list.iterator(); iter.hasNext();) {
            Object[] element = (Object[]) iter.next();
            assertEquals("math",element[0]);
            assertEquals("This module is always available.  It provides access to the\n"+
                          "mathematical functions defined by the C standard.",element[1]);
            
        }
   }

    public void testGetTokenCompletions() throws IOException, CoreException {
        String str = "\n\n\n\n\nimport math\n\n\n#testetestse\n\n\n\n\n";
        List list = shell.getTokenCompletions("math",str);
        assertEquals(29, list.size());
//        for (Iterator iter = list.iterator(); iter.hasNext();) {
//            Object[] element = (Object[]) iter.next();
//            System.out.println(element[0]);
//            System.out.println(element[1]);
//        }
   }


    public void testErrorOnCompletions() throws IOException, CoreException {
        String str = "import math; class C dsdfas d not valid\n";
        List list = shell.getTokenCompletions("math",str);
        assertEquals(1, list.size());
        Object object[] = (Object[]) list.get(0);
        assertEquals("ERROR_COMPLETING",object[0]);
   }
    
   public void testOther(){
       String str = 
		"class C(object):        \n"+  
		"                        \n"+   
		"    def __init__(self): \n"+          
		"                        \n"+  
		"        print dir(self) \n"+      
		"                        \n"+     
		"    def a(self):        \n"+        
		"        pass            \n"+                 
		"                        \n"+         
		"                        \n"+        
		"    def b(self):        \n"+           
		"        self.a          \n"+          
		"                        \n"+        
		"        pass            \n";
       
       List list = shell.getTokenCompletions("C",str);
       assertEquals(17, list.size());
//       for (Iterator iter = list.iterator(); iter.hasNext();) {
//           Object[] element = (Object[]) iter.next();
//           System.out.println(element[0]);
//           System.out.println(element[1]);
//       }
   }
}
