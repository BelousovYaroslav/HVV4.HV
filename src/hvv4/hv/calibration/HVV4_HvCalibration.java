/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv.calibration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 *
 * @author yaroslav
 */
public class HVV4_HvCalibration {
    static Logger logger = Logger.getLogger( HVV4_HvCalibration.class);
    
    private TreeMap m_pCalibration;
    
    public boolean isReady() {
        return ( m_pCalibration.size() >= 2);
    }
    
    public HVV4_HvCalibration( String strCalibFile) {
        m_pCalibration = new TreeMap();
        
        File file = new File( strCalibFile);
        if( !file.exists()) {
            logger.error( "Файл калибровки не найден!");
            return;
        }
        
        try {
            SAXReader reader = new SAXReader();
            URL url = file.toURI().toURL();
            Document document = reader.read( url);
            Element program = document.getRootElement();
            if( program.getName().equals( "Calibration")) {
                // iterate through child elements of root
                for ( Iterator i = program.elementIterator(); i.hasNext(); ) {
                    Element element = ( Element) i.next();

                    String name = element.getName();
                    if( "Code".equals(name)) {
                        int nCode = Integer.parseInt( element.getTextTrim());

                        boolean bValid = true;
                        bValid &= ( element.element( "IMant") != null);
                        bValid &= ( element.element( "IReal") != null);
                        bValid &= ( element.element( "UMant") != null);
                        bValid &= ( element.element( "UReal") != null);

                        if( bValid)
                             m_pCalibration.put( nCode, new Hvv4HvCalibrationUnit( nCode, element));
                        else
                            logger.warn( "В файле калибровки найдены некорректные элементы!");
                    }
                }

            }
            else
                logger.error( "There is no 'Calibration' root-tag in pointed XML");


        } catch( MalformedURLException ex) {
            logger.error( "MalformedURLException caught while loading calibration!", ex);
        } catch( DocumentException ex) {
            logger.error( "DocumentException caught while loading calibration!", ex);
        }
    }
    
    public int GetPcCodeForCurrent( int nDesiredCurrent) {
        if( m_pCalibration == null || m_pCalibration.size() < 2)
            return -1;
        
        Set set = m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nCurr1, nCurr2;
        
        it = set.iterator(); 
        
        Map.Entry entry = (Map.Entry) it.next();
        Hvv4HvCalibrationUnit unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode1 = unit.GetCodePreset();
        nCurr1 = unit.GetCurrent();
                
        entry = (Map.Entry) it.next();
        unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode2 = unit.GetCodePreset();
        nCurr2 = unit.GetCurrent();
            
        while( it.hasNext()) {        
            entry = (Map.Entry) it.next();
            unit = ( Hvv4HvCalibrationUnit) entry.getValue();
            nCode1 = nCode2;
            nCurr1 = nCurr2;
            nCode2 = unit.GetCodePreset();
            nCurr2 = unit.GetCurrent();
            if( nDesiredCurrent <= nCurr2) break;
        }
        
        double k = ( ( double) ( nCode2 - nCode1)) / ( ( double) ( nCurr2 - nCurr1));
        int nResult = nCode1 + ( int) ( k * ( nDesiredCurrent - nCurr1));
        return nResult;
    }
    
    public int GetCurrentForMgCode( int nMgCode) {
        if( m_pCalibration == null || m_pCalibration.size() < 2)
            return -1;
        
        Set set = m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nCurr1, nCurr2;
        
        it = set.iterator(); 
        
        Map.Entry entry = (Map.Entry) it.next();
        Hvv4HvCalibrationUnit unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode1 = unit.GetCodeCurrent();
        nCurr1 = unit.GetCurrent();
                
        entry = (Map.Entry) it.next();
        unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode2 = unit.GetCodeCurrent();
        nCurr2 = unit.GetCurrent();
            
        while( it.hasNext()) {        
            entry = (Map.Entry) it.next();
            unit = ( Hvv4HvCalibrationUnit) entry.getValue();
            nCode1 = nCode2;
            nCurr1 = nCurr2;
            nCode2 = unit.GetCodeCurrent();
            nCurr2 = unit.GetCurrent();
            if( nMgCode <= nCode2) break;
        }
        
        double k = ( ( double) ( nCurr2 - nCurr1)) / ( ( double) ( nCode2 - nCode1));
        int nResult = nCurr1 + ( int) ( k * ( nMgCode - nCode1));
        return nResult;
    }
    
    public int GetVoltageForMgCode( int nMgCode) {
        if( m_pCalibration == null || m_pCalibration.size() < 2)
            return -1;
        
        Set set = m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nVolt1, nVolt2;
        
        it = set.iterator(); 
        
        Map.Entry entry = (Map.Entry) it.next();
        Hvv4HvCalibrationUnit unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode1 = unit.GetCodeVoltage();
        nVolt1 = unit.GetVoltage();
                
        entry = (Map.Entry) it.next();
        unit = ( Hvv4HvCalibrationUnit) entry.getValue();
        nCode2 = unit.GetCodeVoltage();
        nVolt2 = unit.GetVoltage();
            
        while( it.hasNext()) {        
            entry = (Map.Entry) it.next();
            unit = ( Hvv4HvCalibrationUnit) entry.getValue();
            nCode1 = nCode2;
            nVolt1 = nVolt2;
            nCode2 = unit.GetCodeVoltage();
            nVolt2 = unit.GetVoltage();
            if( nMgCode <= nCode2) break;
        }
        
        double k = ( ( double) ( nVolt2 - nVolt1)) / ( ( double) ( nCode2 - nCode1));
        int nResult = nVolt1 + ( int) ( k * ( nMgCode - nCode1));
        return nResult;
    }
}
