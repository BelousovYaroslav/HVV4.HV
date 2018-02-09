/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 *
 * @author yaroslav
 */
public class HVV4_HvSettings {
    static Logger logger = Logger.getLogger(HVV4_HvSettings.class);
    
    private int m_nTimeZoneShift;
    public int GetTimeZoneShift() { return m_nTimeZoneShift;}
    
    private int m_nSingleInstanceSocketServerPort;
    public int GetSingleInstanceSocketServerPort() { return m_nSingleInstanceSocketServerPort;}
    
    private String m_strPort1A;
    public String GetPort1A() { return m_strPort1A; }
    
    private String m_strPort1T;
    public String GetPort1T() { return m_strPort1T; }
    
    private String m_strPort2A;
    public String GetPort2A() { return m_strPort2A; }
    
    private String m_strPort2T;
    public String GetPort2T() { return m_strPort2T; }
    
    private String m_strPort3A;
    public String GetPort3A() { return m_strPort3A; }
    
    private String m_strPort3T;
    public String GetPort3T() { return m_strPort3T; }
    
    private String m_strPort4A;
    public String GetPort4A() { return m_strPort4A; }
    
    private String m_strPort4T;
    public String GetPort4T() { return m_strPort4T; }
    
    public String GetPort( String strId) {
        String strResult;
        switch( strId) {
            case "1A": strResult = GetPort1A(); break;
            case "1T": strResult = GetPort1T(); break;
            case "2A": strResult = GetPort2A(); break;
            case "2T": strResult = GetPort2T(); break;
            case "3A": strResult = GetPort3A(); break;
            case "3T": strResult = GetPort3T(); break;
            case "4A": strResult = GetPort4A(); break;
            case "4T": strResult = GetPort4T(); break;
            default:   strResult = null;        break;
        }
        
        return strResult;
    }
    
    //RW SECTION
    
    
    public HVV4_HvSettings( String strAMSRoot) {
        m_nTimeZoneShift = 0;
        m_nSingleInstanceSocketServerPort = 10006;
        
        m_strPort1A = "/dev/ttyUSB0";
        m_strPort1T = "/dev/ttyUSB1";
        m_strPort2A = "/dev/ttyUSB2";
        m_strPort2T = "/dev/ttyUSB3";
        m_strPort3A = "/dev/ttyUSB4";
        m_strPort3T = "/dev/ttyUSB5";
        m_strPort4A = "/dev/ttyUSB6";
        m_strPort4T = "/dev/ttyUSB7";
        
        ReadSettings();
    }
    
    private boolean ReadSettings() {
        boolean bResOk = true;
        try {
            SAXReader reader = new SAXReader();
            
            String strSettingsFilePathName = System.getenv( "AMS_ROOT") + "/etc/settings.hvv4_hv.r.xml";
            URL url = ( new java.io.File( strSettingsFilePathName)).toURI().toURL();
            
            Document document = reader.read( url);
            
            Element root = document.getRootElement();

            // iterate through child elements of root
            for ( Iterator i = root.elementIterator(); i.hasNext(); ) {
                Element element = ( Element) i.next();
                String name = element.getName();
                String value = element.getText();
                
                //logger.debug( "Pairs: [" + name + " : " + value + "]");
                
                if( "timezone".equals( name)) m_nTimeZoneShift = Integer.parseInt( value);
                
                if( "singleInstancePort.HVV4_hv".equals( name)) m_nSingleInstanceSocketServerPort = Integer.parseInt( value);
                
            }
            
        } catch( MalformedURLException ex) {
            logger.error( "MalformedURLException caught while loading settings!", ex);
            bResOk = false;
        } catch( DocumentException ex) {
            logger.error( "DocumentException caught while loading settings!", ex);
            bResOk = false;
        }
        
        //Коммуникационные порты 
        try {
            SAXReader reader = new SAXReader();
            
            String strSettingsFilePathName = System.getenv( "AMS_ROOT") + "/etc/hvv4.hv.modules.xml";
            URL url = ( new java.io.File( strSettingsFilePathName)).toURI().toURL();
            Document document = reader.read( url);
            Element root = document.getRootElement();

            // iterate through child elements of root
            for ( Iterator i = root.elementIterator(); i.hasNext(); ) {
                Element element = ( Element) i.next();
                String name = element.getName();
                String value = element.getText();
                
                //logger.debug( "Pairs: [" + name + " : " + value + "]");
                
                if( "Port1A".equals( name)) m_strPort1A = value;
                if( "Port1T".equals( name)) m_strPort1T = value;
                if( "Port2A".equals( name)) m_strPort2A = value;
                if( "Port2T".equals( name)) m_strPort2T = value;
                if( "Port3A".equals( name)) m_strPort3A = value;
                if( "Port3T".equals( name)) m_strPort3T = value;
                if( "Port4A".equals( name)) m_strPort4A = value;
                if( "Port4T".equals( name)) m_strPort4T = value;                
            }
            
        } catch( MalformedURLException ex) {
            logger.error( "MalformedURLException caught while loading settings!", ex);
            bResOk = false;
        } catch( DocumentException ex) {
            logger.error( "DocumentException caught while loading settings!", ex);
            bResOk = false;
        }
        
        if( bResOk) {
            try {
                SAXReader reader = new SAXReader();

                String strSettingsFilePathName = System.getenv( "AMS_ROOT") + "/etc/settings.hvv4_hv.rw.xml";
                if( ( new File( strSettingsFilePathName)).exists()) {
                    URL url = ( new java.io.File( strSettingsFilePathName)).toURI().toURL();

                    Document document = reader.read( url);

                    Element root = document.getRootElement();

                    // iterate through child elements of root
                    for ( Iterator i = root.elementIterator(); i.hasNext(); ) {
                        Element element = ( Element) i.next();
                        String name = element.getName();
                        String value = element.getText();

                        //logger.debug( "Pairs: [" + name + " : " + value + "]");
                        if( "nStub".equals( name)) {
                            //do nothing! it is a STUB
                        }
                    }
                }

            } catch( MalformedURLException ex) {
                logger.error( "MalformedURLException caught while loading settings!", ex);
                bResOk = false;
            } catch( DocumentException ex) {
                logger.error( "DocumentException caught while loading settings!", ex);
                bResOk = false;
            }
        }
        
        
        return bResOk;
    }
    
    /**
     * Функция сохранения настроек в .xml файл
     */
    public void SaveSettings() {
        try {
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement( "Settings" );
            
            
            root.addElement( "nStub").addText( "" + 1);
            
            OutputFormat format = OutputFormat.createPrettyPrint();
            
            //TODO
            String strSettingsFilePathName = System.getenv( "AMS_ROOT") + "/etc/settings.poller.rw.xml";
            
            XMLWriter writer = new XMLWriter( new FileWriter( strSettingsFilePathName), format);
            
            writer.write( document );
            writer.close();
        } catch (IOException ex) {
            logger.error( "IOException caught while saving settings!", ex);
        }
    }
}
