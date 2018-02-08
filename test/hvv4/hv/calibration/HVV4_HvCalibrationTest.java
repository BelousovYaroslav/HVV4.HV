/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv.calibration;

import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author yaroslav
 */
public class HVV4_HvCalibrationTest {
    
    HVV4_HvCalibration instance;
    public HVV4_HvCalibrationTest() {
        BasicConfigurator.configure();
        instance = new HVV4_HvCalibration( "testCalibFile.xml");
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of GetPcCodeForCurrent method, of class HVV4_HvCalibration.
     */
    @Test
    public void testGetPcCodeForCurrent() {
        System.out.println("GetPcCodeForCurrent");
        int nDesiredCurrent = 17;
        int expResult = 15;
        int result = instance.GetPcCodeForCurrent(nDesiredCurrent);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of GetCurrentForMgCode method, of class HVV4_HvCalibration.
     */
    @Test
    public void testGetCurrentForMgCode() {
        System.out.println("GetCurrentForMgCode");
        int nMgCode = 16;
        int expResult = 17;
        int result = instance.GetCurrentForMgCode(nMgCode);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
    
    /**
     * Test of GetVoltageForMgCode method, of class HVV4_HvCalibration.
     */
    @Test
    public void testGetVoltageForMgCode() {
        System.out.println("GetVoltageForMgCode");
        
        int nMgCode = 15;
        int expResult = 16;
        int result = instance.GetVoltageForMgCode(nMgCode);
        assertEquals(expResult, result);
        
        nMgCode = 115;
        expResult = 116;
        result = instance.GetVoltageForMgCode(nMgCode);
        assertEquals(expResult, result);
        
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
}
