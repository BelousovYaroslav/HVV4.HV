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
        instance = new HVV4_HvCalibration( "testCalibFileP.xml", "CalibrationP");
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
     * Test of GetValueForCode method, of class HVV4_HvCalibration.
     */
    @Test
    public void testGetValueForCode() {
        System.out.println("GetValueForCode");
        int nDesiredCurrent = 15;
        int expResult = 15;
        int result = instance.GetValForCode( nDesiredCurrent);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
}
