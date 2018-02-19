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
    
    public TreeMap m_pCalibration;
    
    public boolean isReady() {
        return ( m_pCalibration.size() >= 2);
    }
    
    public HVV4_HvCalibration( String strCalibFile, String strRootElement) {
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
            if( program.getName().equals( strRootElement)) {
                // iterate through child elements of root
                for ( Iterator i = program.elementIterator(); i.hasNext(); ) {
                    Element element = ( Element) i.next();

                    String name = element.getName();
                    if( "cpoint".equals(name)) {
                        
                        String strCode  = element.attributeValue( "code");
                        String strValue = element.attributeValue( "value");
                        
                        try {
                            int nCode  = Integer.parseInt( strCode);
                            int nValue = Integer.parseInt( strValue);
                            m_pCalibration.put( nCode, nValue);
                        }
                        catch( NumberFormatException ex) {
                            logger.warn( ex);
                        }
                    }
                    else {
                        logger.warn( "not <cpoint>!");
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
    
    public int GetValForCode( int nCode) {
        if( m_pCalibration == null || m_pCalibration.size() < 2)
            return 0;
        
        Set set = m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nVolt1, nVolt2;
        
        it = set.iterator(); 
       
        Map.Entry entry = (Map.Entry) it.next();
        nCode1 = ( int) entry.getKey();
        nVolt1 = ( int) entry.getValue();
                
        entry = (Map.Entry) it.next();
        nCode2 = ( int) entry.getKey();
        nVolt2 = ( int) entry.getValue();
        
        if( nCode > nCode2) {
            while( it.hasNext()) {        
                entry = (Map.Entry) it.next();
                nCode1 = nCode2;
                nVolt1 = nVolt2;
                nCode2 = ( int) entry.getKey();
                nVolt2 = ( int) entry.getValue();
                if( nCode <= nCode2) break;
            }
        }
        
        double k = ( ( double) ( nVolt2 - nVolt1)) / ( ( double) ( nCode2 - nCode1));
        int nResult = nVolt1 + ( int) ( k * ( nCode - nCode1));
        
        logger.debug( String.format("C>V: CODE1=%d\t VAL1=%d", nCode1, nVolt1));
        logger.debug( String.format("C>V: CODE2=%d\t VAL2=%d", nCode2, nVolt2));
        logger.debug( String.format("C>V: ICODE=%d\tRESLT=%d", nCode,  nResult));
        return nResult;
    }
    
    public int GetCodeForVal( int nVal) {
        if( m_pCalibration == null || m_pCalibration.size() < 2)
            return 0;
        
        Set set = m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nVal1,  nVal2;
        
        it = set.iterator(); 
       
        Map.Entry entry = (Map.Entry) it.next();
        nCode1 = ( int) entry.getKey();
        nVal1  = ( int) entry.getValue();
                
        entry = (Map.Entry) it.next();
        nCode2 = ( int) entry.getKey();
        nVal2  = ( int) entry.getValue();
        
        if( nVal > nVal2) {
            while( it.hasNext()) {        
                entry = (Map.Entry) it.next();
                nCode1 = nCode2;
                nVal1  = nVal2;
                nCode2 = ( int) entry.getKey();
                nVal2 = ( int) entry.getValue();
                if( nVal <= nVal2) break;
            }
        }
        
        double k = ( ( double) ( nCode2 - nCode1)) / ( ( double) ( nVal2 - nVal1));
        int nResult = nCode1 + ( int) ( k * ( nVal - nVal1));
        
        logger.debug( String.format("V>C: CODE1=%d\t VAL1=%d", nCode1, nVal1));
        logger.debug( String.format("V>C: CODE2=%d\t VAL2=%d", nCode2, nVal2));
        logger.debug( String.format("V>C: IVAL=%d \t RESLT=%d",  nVal,  nResult));
        return nResult;
    }
}
