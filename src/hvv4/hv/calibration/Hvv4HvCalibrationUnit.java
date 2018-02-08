/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv.calibration;

import org.dom4j.Element;

/**
 *
 * @author yaroslav
 */
public class Hvv4HvCalibrationUnit {
    boolean bValid;
    
    private final int m_nCodePreset;
    public int GetCodePreset() { return m_nCodePreset; }
    
    private final int m_nCodeCurrent;
    public int GetCodeCurrent() { return m_nCodeCurrent; }
    
    private final int m_nCodeVoltage;
    public int GetCodeVoltage() { return m_nCodeVoltage; }
    
    private final int m_nCurrent;
    public int GetCurrent() { return m_nCurrent; }
    
    private final int m_nVoltage;
    public int GetVoltage() { return m_nVoltage; }
    
    public Hvv4HvCalibrationUnit() {
        m_nCodePreset   = 0;
        m_nCodeCurrent  = 0;
        m_nCodeVoltage  = 0;
        m_nCurrent      = 0;
        m_nVoltage      = 0;
        bValid = false;
    }
    
    public Hvv4HvCalibrationUnit( int nCodePreset, int nCodeCurrent, int nCodeVoltage, int nCurrent, int nVoltage) {
        m_nCodePreset   = nCodePreset;
        m_nCodeCurrent  = nCodeCurrent;
        m_nCodeVoltage  = nCodeVoltage;
        m_nCurrent      = nCurrent;
        m_nVoltage      = nVoltage;
        bValid = true;
    }
    
    public Hvv4HvCalibrationUnit( int nCodePreset, Element e) {
        m_nCodePreset  = nCodePreset;
        m_nCodeCurrent = Integer.parseInt( e.element( "IMant").getText());
        m_nCodeVoltage = Integer.parseInt( e.element( "UMant").getText());
        m_nCurrent     = Integer.parseInt( e.element( "IReal").getText());
        m_nVoltage     = Integer.parseInt( e.element( "UReal").getText());
        bValid = true;
    }
}
