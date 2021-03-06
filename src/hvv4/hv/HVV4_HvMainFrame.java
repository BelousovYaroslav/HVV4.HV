/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

import hvv4.hv.calibration.HVV4_HvCalibration;
import hvv4.hv.comm.HVV4_HV_CircleBuffer;
import hvv4.hv.comm.HVV4_HV_StreamProcessingThread;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JLabel;
import javax.swing.Timer;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.apache.log4j.Logger;

/**
 *
 * @author yaroslav
 */
public class HVV4_HvMainFrame extends javax.swing.JFrame {

    static Logger logger = Logger.getLogger( HVV4_HvMainFrame.class);
    
    private HVV4_HvApp theApp;

    Timer tRefreshValues;
    Timer tRefreshStates;
    Timer tPolling;
    Timer tCommandSend;
    Timer tApplyPreset;
    
    int m_nEmergencyOffClicks;
    Timer tEmergencyOffClicksDrop;
    
    int m_nReconnectClicks;
    String m_strReconnectElectrode;
    Timer tReconnectClicksDrop;
    
    /**
     * Creates new form HVV4HVMainFrame
     */
    public HVV4_HvMainFrame( HVV4_HvApp app) {
        initComponents();
        
        theApp = app;
        
        String strOS = System.getProperty("os.name");
        logger.info( "OS:" + strOS);
        if( strOS.contains("indows")) {
            //setResizable( true);
            Dimension d = getSize();
            d.height += theApp.GetSettings().GetDialogHeightAddWin();
            setSize( d);
            //setResizable( false);
        }
        
        if( theApp.GetSettings().GetUseCalibToMg()) {
            lblSetCurrentTitle.setText( "Уставка выходного тока, мкА:");
            sldPreset.setMaximum( 5000);
        }
        else {
            lblSetCurrentTitle.setText( "Уставка выходного тока, код:");
            sldPreset.setMaximum( 32767);
        }
        btnPresetUp.setIcon( theApp.GetResources().getIconUp());
        btnPresetDown.setIcon( theApp.GetResources().getIconTriaDown());
        
        m_nEmergencyOffClicks = 0;
        tEmergencyOffClicksDrop = new Timer( 500, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                m_nEmergencyOffClicks = 0;
                tEmergencyOffClicksDrop.stop();
                lblEmergencyOff.setBackground( null);
            }
        });
        
        m_nReconnectClicks = 0;
        m_strReconnectElectrode = null;
        tReconnectClicksDrop = new Timer( 500, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                m_nReconnectClicks = 0;
                tReconnectClicksDrop.stop();
                if( m_strReconnectElectrode != null) {
                    switch( m_strReconnectElectrode) {
                        //case "1A": lbl1A.setBackground( null); break;
                        case "1T": lbl1T.setBackground( null); break;
                        //case "2A": lbl2A.setBackground( null); break;
                        //case "2T": lbl2T.setBackground( null); break;
                        //case "3A": lbl3A.setBackground( null); break;
                        //case "3T": lbl3T.setBackground( null); break;
                        //case "4A": lbl4A.setBackground( null); break;
                        //case "4T": lbl4T.setBackground( null); break;
                    }
                }
                
            }
        });
                
        setTitle( "HVV4.Модуль управления  в\\в. (2018.03.02 14:00), (C) ФЛАВТ 2018.");
        
        //таймер, отправляющий команды выставки уставки в контроллеры в/в модулей
        tApplyPreset = new Timer( 300, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                tApplyPreset.stop();
                int value = sldPreset.getValue();
                int nApplyCode;
                Set set = theApp.m_mapSerials.entrySet();
                Iterator it = set.iterator();
                while( it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    
                    String strId = ( String) entry.getKey();
                    SerialPort port = ( SerialPort) entry.getValue();
                    
                    ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( strId);
                    boolean bApply = false;
                    switch( strId) {
                        case "1A": if( rad1AOn.isSelected()) bApply = true; break;
                        case "1T": if( rad1TOn.isSelected()) bApply = true; break;
                        case "2A": if( rad2AOn.isSelected()) bApply = true; break;
                        case "2T": if( rad2TOn.isSelected()) bApply = true; break;
                        case "3A": if( rad3AOn.isSelected()) bApply = true; break;
                        case "3T": if( rad3TOn.isSelected()) bApply = true; break;
                        case "4A": if( rad4AOn.isSelected()) bApply = true; break;
                        case "4T": if( rad4TOn.isSelected()) bApply = true; break;
                    }
                    
                    if( bApply) {
                        nApplyCode = value;
                        if( theApp.GetSettings().GetUseCalibToMg() && theApp.m_mapCalibrationP != null) {
                            HVV4_HvCalibration calib = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( strId);
                            nApplyCode = calib.GetCodeForVal( nApplyCode);
                        }
                    
                        byte aBytes[] = new byte[3];
                        aBytes[0] = 0x01;
                        aBytes[1] = ( byte) ( nApplyCode & 0xFF);
                        aBytes[2] = ( byte) ( ( nApplyCode & 0xFF00) >> 8);
                        
                        
                        q.add( aBytes);
                        logger.info( String.format( strId + ": SET PRESET (0x%02X 0x%02X 0x%02X): queued", aBytes[0], aBytes[1], aBytes[2]));
                        
                        byte bBytes[] = new byte[1];
                        bBytes[0] = 0x02;
                        q.add( bBytes);
                        logger.info( String.format( strId + ": APPLY (0x%02X): queued", bBytes[0]));
                    }
                }
            }
            
        });
        //СТАРТОВАТЬ НЕ НАДО!
                
        //таймер, отправляющий команды в контроллеры в/в модулей
        tCommandSend = new Timer( 100, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Set set = theApp.m_mapSerials.entrySet();
                Iterator it = set.iterator();
                while( it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    
                    String strId = ( String) entry.getKey();
                    SerialPort port = ( SerialPort) entry.getValue();
                    
                    HVV4_HV_StreamProcessingThread proc = ( HVV4_HV_StreamProcessingThread) theApp.m_mapProcessorsRunnables.get( strId);
                    if( proc.GetWaitingForResponse() == false) {
                        
                        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( strId);
                        if( !q.isEmpty()) {
                            byte aBytes[] = ( byte []) q.poll();

                            String strCmd = "";
                            for( int i=0; i<aBytes.length; i++)
                                strCmd += String.format( " 0x%02X", aBytes[i]);
                            
                            try {
                                port.writeBytes( aBytes);
                                proc.WaitForRespond();
                                logger.trace( strId + ": SEND CMD: (" + strCmd + ") sent");
                            } catch (SerialPortException ex) {
                                logger.error( strId + ": SEND CMD: FAIL: COM-Communication exception", ex);
                            }
                        }
                        else
                            logger.trace( strId + ": SEND CMD: EMPTY");
                    }
                    else {
                        logger.warn( strId + ": SEND CMD: BUSY (waiting for result)");
                    }
                }
            }
            
        });
        tCommandSend.start();

        //таймер, обновляющий значения на экране
        tRefreshValues = new Timer( 500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int nVal;
                
                String strU1A = "", strU2A = "", strU3A = "", strU4A = "";
                String strU1T = "", strU2T = "", strU3T = "", strU4T = "";
                String strI1A = "", strI2A = "", strI3A = "", strI4A = "";
                String strI1T = "", strI2T = "", strI3T = "", strI4T = "";
                
                if( theApp.m_mapU.containsKey( "1A")) { nVal = ( int) theApp.m_mapU.get( "1A"); strU1A = String.format( "%d", nVal); }
                if( theApp.m_mapU.containsKey( "2A")) { nVal = ( int) theApp.m_mapU.get( "2A"); strU2A = String.format( "%d", nVal); }
                if( theApp.m_mapU.containsKey( "3A")) { nVal = ( int) theApp.m_mapU.get( "3A"); strU3A = "" + nVal; }
                if( theApp.m_mapU.containsKey( "4A")) { nVal = ( int) theApp.m_mapU.get( "4A"); strU4A = "" + nVal; }
                
                if( theApp.m_mapU.containsKey( "1T")) { nVal = ( int) theApp.m_mapU.get( "1T"); strU1T = "" + nVal; }
                if( theApp.m_mapU.containsKey( "2T")) { nVal = ( int) theApp.m_mapU.get( "2T"); strU2T = String.format( "%d", nVal); }
                if( theApp.m_mapU.containsKey( "3T")) { nVal = ( int) theApp.m_mapU.get( "3T"); strU3T = "" + nVal; }
                if( theApp.m_mapU.containsKey( "4T")) { nVal = ( int) theApp.m_mapU.get( "4T"); strU4T = "" + nVal; }
                
                if( theApp.m_mapI.containsKey( "1A")) { nVal = ( int) theApp.m_mapI.get( "1A"); strI1A = String.format( "%d", nVal); }
                if( theApp.m_mapI.containsKey( "2A")) { nVal = ( int) theApp.m_mapI.get( "2A"); strI2A = String.format( "%d", nVal); }
                if( theApp.m_mapI.containsKey( "3A")) { nVal = ( int) theApp.m_mapI.get( "3A"); strI3A = "" + nVal; }
                if( theApp.m_mapI.containsKey( "4A")) { nVal = ( int) theApp.m_mapI.get( "4A"); strI4A = "" + nVal; }
                
                if( theApp.m_mapI.containsKey( "1T")) { nVal = ( int) theApp.m_mapI.get( "1T"); strI1T = "" + nVal; }
                if( theApp.m_mapI.containsKey( "2T")) { nVal = ( int) theApp.m_mapI.get( "2T"); strI2T = String.format( "%d", nVal); }
                if( theApp.m_mapI.containsKey( "3T")) { nVal = ( int) theApp.m_mapI.get( "3T"); strI3T = "" + nVal; }
                if( theApp.m_mapI.containsKey( "4T")) { nVal = ( int) theApp.m_mapI.get( "4T"); strI4T = "" + nVal; }
                
                lblU1A.setText( strU1A);    lblU2A.setText( strU2A);
                lblU3A.setText( strU3A);    lblU4A.setText( strU4A);
                lblI1A.setText( strI1A);    lblI2A.setText( strI2A);
                lblI3A.setText( strI3A);    lblI4A.setText( strI4A);
                
                lblU1T.setText( strU1T);    lblU2T.setText( strU2T);
                lblU3T.setText( strU3T);    lblU4T.setText( strU4T);
                lblI1T.setText( strI1T);    lblI2T.setText( strI2T);
                lblI3T.setText( strI3T);    lblI4T.setText( strI4T);
            }
            
            
        });
        tRefreshValues.start();
        
        tRefreshStates = new Timer( 200, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                edtPreset.setEnabled(       !tglBtnLockScreen.isSelected());
                btnPresetUp.setEnabled(     !tglBtnLockScreen.isSelected());
                btnPresetDown.setEnabled(   !tglBtnLockScreen.isSelected());
                sldPreset.setEnabled(       !tglBtnLockScreen.isSelected());
                btnPresetApply.setEnabled(  !tglBtnLockScreen.isSelected());
                btnTurnOff.setEnabled(      !tglBtnLockScreen.isSelected());
  
                btnPreset500.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset600.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset1000.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset1100.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset1200.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset2500.setEnabled(   !tglBtnLockScreen.isSelected());
                btnPreset3500.setEnabled(   !tglBtnLockScreen.isSelected());
                
                //btn1A.setEnabled(           !tglBtnLockScreen.isSelected());
                rad1AOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad1AOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad1AStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn2A.setEnabled(           !tglBtnLockScreen.isSelected());
                rad2AOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad2AOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad2AStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn3A.setEnabled(           !tglBtnLockScreen.isSelected());
                rad3AOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad3AOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad3AStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn4A.setEnabled(           !tglBtnLockScreen.isSelected());
                rad4AOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad4AOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad4AStay.setEnabled(       !tglBtnLockScreen.isSelected());

                //btn1T.setEnabled(           !tglBtnLockScreen.isSelected());
                rad1TOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad1TOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad1TStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn2T.setEnabled(           !tglBtnLockScreen.isSelected());
                rad2TOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad2TOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad2TStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn3T.setEnabled(           !tglBtnLockScreen.isSelected());
                rad3TOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad3TOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad3TStay.setEnabled(       !tglBtnLockScreen.isSelected());
                //btn4T.setEnabled(           !tglBtnLockScreen.isSelected());
                rad4TOff.setEnabled(        !tglBtnLockScreen.isSelected());
                rad4TOn.setEnabled(         !tglBtnLockScreen.isSelected());
                rad4TStay.setEnabled(       !tglBtnLockScreen.isSelected());
                
                btnExit.setEnabled(         !tglBtnLockScreen.isSelected());
                
                if( theApp.m_mapCalibrationP == null) return;
                if( theApp.m_mapCalibrationI == null) return;
                if( theApp.m_mapCalibrationU == null) return;
                
                Color clr = null;
                SerialPort port = ( SerialPort) theApp.m_mapSerials.get( "1A");
                HVV4_HvCalibration calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "1A");
                HVV4_HvCalibration calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "1A");
                HVV4_HvCalibration calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "1A");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl1A.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "2A");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "2A");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "2A");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "2A");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl2A.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "3A");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "3A");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "3A");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "3A");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl3A.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "4A");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "4A");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "4A");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "4A");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl4A.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "1T");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "1T");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "1T");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "1T");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl1T.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "2T");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "2T");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "2T");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "2T");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl2T.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "3T");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "3T");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "3T");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "3T");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl3T.setForeground( clr);
                
                clr = null;
                port = ( SerialPort) theApp.m_mapSerials.get( "4T");
                calibP = ( HVV4_HvCalibration) theApp.m_mapCalibrationP.get( "4T");
                calibI = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( "4T");
                calibU = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( "4T");
                if( calibP == null || calibP.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibI == null || calibI.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( calibU == null || calibU.m_pCalibration.size() < 2) clr = Color.YELLOW;
                if( port == null  || !port.isOpened())   clr = Color.RED;
                lbl4T.setForeground( clr);
            }
        });
        tRefreshStates.start();
        
        //таймер, периодически (при пустой очереди команд по каналу, добавляющий команду опроса текущих значений)
        tPolling = new Timer( 200, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                logger.trace( "POLLING");
                Set set = theApp.m_mapSerials.entrySet();
                Iterator it = set.iterator();
                while( it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    
                    String strId = ( String) entry.getKey();
                    SerialPort port = ( SerialPort) entry.getValue();
                    
                    ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( strId);
                    if( q.isEmpty()) {
                        byte aBytes[] = new byte[1];
                        aBytes[0] = 0x05;
                        q.add( aBytes);
                        logger.trace( strId + ": POLLING: queued");
                    }
                    else {
                        logger.info( strId + ": POLLING: REJECTED");
                    }
        
                }
            }
            
        });
        tPolling.start();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnGroup1A = new javax.swing.ButtonGroup();
        btnGroup2A = new javax.swing.ButtonGroup();
        btnGroup3A = new javax.swing.ButtonGroup();
        btnGroup4A = new javax.swing.ButtonGroup();
        btnGroup1T = new javax.swing.ButtonGroup();
        btnGroup2T = new javax.swing.ButtonGroup();
        btnGroup3T = new javax.swing.ButtonGroup();
        btnGroup4T = new javax.swing.ButtonGroup();
        lblSetCurrentTitle = new javax.swing.JLabel();
        edtPreset = new javax.swing.JTextField();
        btnPresetDown = new javax.swing.JButton();
        btnPresetUp = new javax.swing.JButton();
        sldPreset = new javax.swing.JSlider();
        btnPresetApply = new javax.swing.JButton();
        btnTurnOff = new javax.swing.JButton();
        rad1AOn = new javax.swing.JRadioButton();
        rad1AOff = new javax.swing.JRadioButton();
        rad1AStay = new javax.swing.JRadioButton();
        rad2AOn = new javax.swing.JRadioButton();
        rad2AOff = new javax.swing.JRadioButton();
        rad2AStay = new javax.swing.JRadioButton();
        rad3AOn = new javax.swing.JRadioButton();
        rad3AOff = new javax.swing.JRadioButton();
        rad3AStay = new javax.swing.JRadioButton();
        rad4AOn = new javax.swing.JRadioButton();
        rad4AOff = new javax.swing.JRadioButton();
        rad4AStay = new javax.swing.JRadioButton();
        lblVoltageATitle = new javax.swing.JLabel();
        lblU1A = new javax.swing.JLabel();
        lblU2A = new javax.swing.JLabel();
        lblU3A = new javax.swing.JLabel();
        lblU4A = new javax.swing.JLabel();
        lblCurrentATitle = new javax.swing.JLabel();
        lblI1A = new javax.swing.JLabel();
        lblI2A = new javax.swing.JLabel();
        lblI3A = new javax.swing.JLabel();
        lblI4A = new javax.swing.JLabel();
        rad1TOn = new javax.swing.JRadioButton();
        rad1TOff = new javax.swing.JRadioButton();
        rad1TStay = new javax.swing.JRadioButton();
        rad2TOn = new javax.swing.JRadioButton();
        rad2TOff = new javax.swing.JRadioButton();
        rad2TStay = new javax.swing.JRadioButton();
        rad3TOn = new javax.swing.JRadioButton();
        rad3TOff = new javax.swing.JRadioButton();
        rad3TStay = new javax.swing.JRadioButton();
        rad4TOn = new javax.swing.JRadioButton();
        rad4TOff = new javax.swing.JRadioButton();
        rad4TStay = new javax.swing.JRadioButton();
        lblVoltageTTitle = new javax.swing.JLabel();
        lblU1T = new javax.swing.JLabel();
        lblU2T = new javax.swing.JLabel();
        lblU3T = new javax.swing.JLabel();
        lblU4T = new javax.swing.JLabel();
        lblCurrentTTitle = new javax.swing.JLabel();
        lblI1T = new javax.swing.JLabel();
        lblI2T = new javax.swing.JLabel();
        lblI3T = new javax.swing.JLabel();
        lblI4T = new javax.swing.JLabel();
        tglBtnLockScreen = new javax.swing.JToggleButton();
        lblEmergencyOff = new javax.swing.JLabel();
        btnExit = new javax.swing.JButton();
        btnPreset500 = new javax.swing.JButton();
        btnPreset600 = new javax.swing.JButton();
        btnPreset1000 = new javax.swing.JButton();
        btnPreset1100 = new javax.swing.JButton();
        lbl1T = new javax.swing.JLabel();
        lbl2T = new javax.swing.JLabel();
        lbl3T = new javax.swing.JLabel();
        lbl4T = new javax.swing.JLabel();
        lbl1A = new javax.swing.JLabel();
        lbl3A = new javax.swing.JLabel();
        lbl4A = new javax.swing.JLabel();
        lbl2A = new javax.swing.JLabel();
        btnPreset1200 = new javax.swing.JButton();
        btnPreset2500 = new javax.swing.JButton();
        btnPreset3500 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(1070, 680));
        setResizable(false);
        addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                formMouseWheelMoved(evt);
            }
        });
        getContentPane().setLayout(null);

        lblSetCurrentTitle.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        lblSetCurrentTitle.setText("Уставка выходного тока, мкА:");
        getContentPane().add(lblSetCurrentTitle);
        lblSetCurrentTitle.setBounds(310, 10, 380, 40);

        edtPreset.setFont(new java.awt.Font("Dialog", 0, 32)); // NOI18N
        edtPreset.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        edtPreset.setText("1000");
        edtPreset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edtPresetActionPerformed(evt);
            }
        });
        getContentPane().add(edtPreset);
        edtPreset.setBounds(690, 5, 130, 50);

        btnPresetDown.setIcon(new javax.swing.ImageIcon("/home/yaroslav/HVV_HOME/res/images/down.gif")); // NOI18N
        btnPresetDown.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetDownActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetDown);
        btnPresetDown.setBounds(820, 35, 40, 20);

        btnPresetUp.setIcon(new javax.swing.ImageIcon("/home/yaroslav/HVV_HOME/res/images/up.gif")); // NOI18N
        btnPresetUp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetUpActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetUp);
        btnPresetUp.setBounds(820, 5, 40, 20);

        sldPreset.setMaximum(15000);
        sldPreset.setToolTipText("");
        sldPreset.setValue(1000);
        sldPreset.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                sldPresetMouseWheelMoved(evt);
            }
        });
        sldPreset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                sldPresetMouseReleased(evt);
            }
        });
        sldPreset.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                sldPresetMouseDragged(evt);
            }
        });
        getContentPane().add(sldPreset);
        sldPreset.setBounds(10, 45, 1050, 30);

        btnPresetApply.setBackground(new java.awt.Color(255, 50, 50));
        btnPresetApply.setText("Подать / обновить");
        btnPresetApply.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetApplyActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetApply);
        btnPresetApply.setBounds(10, 80, 170, 50);

        btnTurnOff.setBackground(new java.awt.Color(100, 255, 50));
        btnTurnOff.setText("Снять");
        btnTurnOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTurnOffActionPerformed(evt);
            }
        });
        getContentPane().add(btnTurnOff);
        btnTurnOff.setBounds(940, 80, 110, 50);

        btnGroup1A.add(rad1AOn);
        rad1AOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1AOn.setText("Вкл.");
        getContentPane().add(rad1AOn);
        rad1AOn.setBounds(110, 140, 100, 30);

        btnGroup1A.add(rad1AOff);
        rad1AOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1AOff.setSelected(true);
        rad1AOff.setText("Выкл.");
        rad1AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad1AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad1AOff);
        rad1AOff.setBounds(110, 170, 100, 30);

        btnGroup1A.add(rad1AStay);
        rad1AStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1AStay.setText("Ост.");
        getContentPane().add(rad1AStay);
        rad1AStay.setBounds(110, 200, 100, 30);

        btnGroup2A.add(rad2AOn);
        rad2AOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2AOn.setText("Вкл.");
        getContentPane().add(rad2AOn);
        rad2AOn.setBounds(350, 140, 100, 30);

        btnGroup2A.add(rad2AOff);
        rad2AOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2AOff.setSelected(true);
        rad2AOff.setText("Выкл.");
        rad2AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad2AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad2AOff);
        rad2AOff.setBounds(350, 170, 100, 30);

        btnGroup2A.add(rad2AStay);
        rad2AStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2AStay.setText("Ост.");
        getContentPane().add(rad2AStay);
        rad2AStay.setBounds(350, 200, 100, 30);

        btnGroup3A.add(rad3AOn);
        rad3AOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3AOn.setText("Вкл.");
        getContentPane().add(rad3AOn);
        rad3AOn.setBounds(590, 140, 100, 30);

        btnGroup3A.add(rad3AOff);
        rad3AOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3AOff.setSelected(true);
        rad3AOff.setText("Выкл.");
        rad3AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad3AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad3AOff);
        rad3AOff.setBounds(590, 170, 100, 30);

        btnGroup3A.add(rad3AStay);
        rad3AStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3AStay.setText("Ост.");
        getContentPane().add(rad3AStay);
        rad3AStay.setBounds(590, 200, 100, 30);

        btnGroup4A.add(rad4AOn);
        rad4AOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4AOn.setText("Вкл.");
        getContentPane().add(rad4AOn);
        rad4AOn.setBounds(830, 140, 100, 30);

        btnGroup4A.add(rad4AOff);
        rad4AOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4AOff.setSelected(true);
        rad4AOff.setText("Выкл.");
        rad4AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad4AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad4AOff);
        rad4AOff.setBounds(830, 170, 100, 30);

        btnGroup4A.add(rad4AStay);
        rad4AStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4AStay.setText("Ост.");
        getContentPane().add(rad4AStay);
        rad4AStay.setBounds(830, 200, 100, 30);

        lblVoltageATitle.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblVoltageATitle.setText("U, В");
        getContentPane().add(lblVoltageATitle);
        lblVoltageATitle.setBounds(10, 230, 100, 70);

        lblU1A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU1A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU1A.setText("1000");
        lblU1A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU1A);
        lblU1A.setBounds(110, 230, 230, 70);

        lblU2A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU2A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU2A.setText("1000");
        lblU2A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU2A);
        lblU2A.setBounds(350, 230, 230, 70);

        lblU3A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU3A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU3A.setText("1000");
        lblU3A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU3A);
        lblU3A.setBounds(590, 230, 230, 70);

        lblU4A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU4A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU4A.setText("1000");
        lblU4A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU4A);
        lblU4A.setBounds(830, 230, 230, 70);

        lblCurrentATitle.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblCurrentATitle.setText("I, мкА");
        getContentPane().add(lblCurrentATitle);
        lblCurrentATitle.setBounds(10, 300, 100, 70);

        lblI1A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI1A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI1A.setText("1000");
        lblI1A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI1A);
        lblI1A.setBounds(110, 300, 230, 70);

        lblI2A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI2A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI2A.setText("1000");
        lblI2A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI2A);
        lblI2A.setBounds(350, 300, 230, 70);

        lblI3A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI3A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI3A.setText("1000");
        lblI3A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI3A);
        lblI3A.setBounds(590, 300, 230, 70);

        lblI4A.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI4A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI4A.setText("1000");
        lblI4A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI4A);
        lblI4A.setBounds(830, 300, 230, 70);

        btnGroup1T.add(rad1TOn);
        rad1TOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1TOn.setText("Вкл.");
        getContentPane().add(rad1TOn);
        rad1TOn.setBounds(110, 380, 100, 30);

        btnGroup1T.add(rad1TOff);
        rad1TOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1TOff.setSelected(true);
        rad1TOff.setText("Выкл.");
        rad1TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad1TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad1TOff);
        rad1TOff.setBounds(110, 410, 100, 30);

        btnGroup1T.add(rad1TStay);
        rad1TStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad1TStay.setText("Ост.");
        getContentPane().add(rad1TStay);
        rad1TStay.setBounds(110, 440, 100, 30);

        btnGroup2T.add(rad2TOn);
        rad2TOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2TOn.setText("Вкл.");
        getContentPane().add(rad2TOn);
        rad2TOn.setBounds(350, 380, 100, 30);

        btnGroup2T.add(rad2TOff);
        rad2TOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2TOff.setSelected(true);
        rad2TOff.setText("Выкл.");
        rad2TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad2TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad2TOff);
        rad2TOff.setBounds(350, 410, 100, 30);

        btnGroup2T.add(rad2TStay);
        rad2TStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad2TStay.setText("Ост.");
        getContentPane().add(rad2TStay);
        rad2TStay.setBounds(350, 440, 100, 30);

        btnGroup3T.add(rad3TOn);
        rad3TOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3TOn.setText("Вкл.");
        getContentPane().add(rad3TOn);
        rad3TOn.setBounds(590, 380, 100, 30);

        btnGroup3T.add(rad3TOff);
        rad3TOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3TOff.setSelected(true);
        rad3TOff.setText("Выкл.");
        rad3TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad3TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad3TOff);
        rad3TOff.setBounds(590, 410, 100, 30);

        btnGroup3T.add(rad3TStay);
        rad3TStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad3TStay.setText("Ост.");
        getContentPane().add(rad3TStay);
        rad3TStay.setBounds(590, 440, 100, 30);

        btnGroup4T.add(rad4TOn);
        rad4TOn.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4TOn.setText("Вкл.");
        getContentPane().add(rad4TOn);
        rad4TOn.setBounds(830, 380, 100, 30);

        btnGroup4T.add(rad4TOff);
        rad4TOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4TOff.setSelected(true);
        rad4TOff.setText("Выкл.");
        rad4TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad4TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad4TOff);
        rad4TOff.setBounds(830, 410, 100, 30);

        btnGroup4T.add(rad4TStay);
        rad4TStay.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        rad4TStay.setText("Ост.");
        getContentPane().add(rad4TStay);
        rad4TStay.setBounds(830, 440, 100, 30);

        lblVoltageTTitle.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblVoltageTTitle.setText("U, В");
        getContentPane().add(lblVoltageTTitle);
        lblVoltageTTitle.setBounds(10, 470, 100, 70);

        lblU1T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU1T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU1T.setText("1000");
        lblU1T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU1T);
        lblU1T.setBounds(110, 470, 230, 70);

        lblU2T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU2T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU2T.setText("1000");
        lblU2T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU2T);
        lblU2T.setBounds(350, 470, 230, 70);

        lblU3T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU3T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU3T.setText("1000");
        lblU3T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU3T);
        lblU3T.setBounds(590, 470, 230, 70);

        lblU4T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblU4T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU4T.setText("1000");
        lblU4T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU4T);
        lblU4T.setBounds(830, 470, 230, 70);

        lblCurrentTTitle.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblCurrentTTitle.setText("I, мкА");
        getContentPane().add(lblCurrentTTitle);
        lblCurrentTTitle.setBounds(10, 540, 100, 70);

        lblI1T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI1T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI1T.setText("1000");
        lblI1T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI1T);
        lblI1T.setBounds(110, 540, 230, 70);

        lblI2T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI2T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI2T.setText("1000");
        lblI2T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI2T);
        lblI2T.setBounds(350, 540, 230, 70);

        lblI3T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI3T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI3T.setText("1000");
        lblI3T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI3T);
        lblI3T.setBounds(590, 540, 230, 70);

        lblI4T.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        lblI4T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI4T.setText("1000");
        lblI4T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI4T);
        lblI4T.setBounds(830, 540, 230, 70);

        tglBtnLockScreen.setText("Заблокировать экран");
        tglBtnLockScreen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tglBtnLockScreenActionPerformed(evt);
            }
        });
        getContentPane().add(tglBtnLockScreen);
        tglBtnLockScreen.setBounds(10, 620, 200, 50);

        lblEmergencyOff.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        lblEmergencyOff.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblEmergencyOff.setText("АВАРИЙНОЕ ВЫКЛЮЧЕНИЕ. ТРОЙНОЙ КЛИК СЮДА!");
        lblEmergencyOff.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        lblEmergencyOff.setOpaque(true);
        lblEmergencyOff.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lblEmergencyOffMouseClicked(evt);
            }
        });
        getContentPane().add(lblEmergencyOff);
        lblEmergencyOff.setBounds(220, 620, 690, 50);

        btnExit.setText("Выход");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });
        getContentPane().add(btnExit);
        btnExit.setBounds(920, 620, 140, 50);

        btnPreset500.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset500.setText("500");
        btnPreset500.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset500ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset500);
        btnPreset500.setBounds(190, 80, 90, 50);

        btnPreset600.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset600.setText("600");
        btnPreset600.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset600ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset600);
        btnPreset600.setBounds(290, 80, 90, 50);

        btnPreset1000.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset1000.setText("1000");
        btnPreset1000.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset1000ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset1000);
        btnPreset1000.setBounds(410, 80, 90, 50);

        btnPreset1100.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset1100.setText("1100");
        btnPreset1100.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset1100ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset1100);
        btnPreset1100.setBounds(510, 80, 90, 50);

        lbl1T.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl1T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl1T.setText("1Ш");
        lbl1T.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl1T.setOpaque(true);
        lbl1T.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl1TMouseClicked(evt);
            }
        });
        getContentPane().add(lbl1T);
        lbl1T.setBounds(220, 380, 120, 80);

        lbl2T.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl2T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl2T.setText("2Ш");
        lbl2T.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl2T.setOpaque(true);
        lbl2T.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl2TMouseClicked(evt);
            }
        });
        getContentPane().add(lbl2T);
        lbl2T.setBounds(450, 380, 120, 80);

        lbl3T.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl3T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl3T.setText("3Ш");
        lbl3T.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl3T.setOpaque(true);
        lbl3T.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl3TMouseClicked(evt);
            }
        });
        getContentPane().add(lbl3T);
        lbl3T.setBounds(700, 380, 120, 80);

        lbl4T.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl4T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl4T.setText("4Ш");
        lbl4T.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl4T.setOpaque(true);
        lbl4T.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl4TMouseClicked(evt);
            }
        });
        getContentPane().add(lbl4T);
        lbl4T.setBounds(940, 380, 120, 80);

        lbl1A.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl1A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl1A.setText("1А");
        lbl1A.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl1A.setOpaque(true);
        lbl1A.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl1AMouseClicked(evt);
            }
        });
        getContentPane().add(lbl1A);
        lbl1A.setBounds(220, 140, 120, 80);

        lbl3A.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl3A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl3A.setText("3А");
        lbl3A.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl3A.setOpaque(true);
        lbl3A.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl3AMouseClicked(evt);
            }
        });
        getContentPane().add(lbl3A);
        lbl3A.setBounds(700, 140, 120, 80);

        lbl4A.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl4A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl4A.setText("4А");
        lbl4A.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl4A.setOpaque(true);
        lbl4A.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl4AMouseClicked(evt);
            }
        });
        getContentPane().add(lbl4A);
        lbl4A.setBounds(940, 140, 120, 80);

        lbl2A.setFont(new java.awt.Font("Dialog", 0, 48)); // NOI18N
        lbl2A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl2A.setText("2А");
        lbl2A.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(220, 220, 220), 1, true));
        lbl2A.setOpaque(true);
        lbl2A.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl2AMouseClicked(evt);
            }
        });
        getContentPane().add(lbl2A);
        lbl2A.setBounds(460, 140, 120, 80);

        btnPreset1200.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset1200.setText("1200");
        btnPreset1200.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset1200ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset1200);
        btnPreset1200.setBounds(610, 80, 90, 50);

        btnPreset2500.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset2500.setText("2500");
        btnPreset2500.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset2500ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset2500);
        btnPreset2500.setBounds(730, 80, 90, 50);

        btnPreset3500.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        btnPreset3500.setText("3500");
        btnPreset3500.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPreset3500ActionPerformed(evt);
            }
        });
        getContentPane().add(btnPreset3500);
        btnPreset3500.setBounds(830, 80, 90, 50);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExitActionPerformed
        tRefreshValues.stop();
        tRefreshStates.stop();
        tPolling.stop();
        tCommandSend.stop();
        theApp.ClosePorts();
        this.dispose();
    }//GEN-LAST:event_btnExitActionPerformed

    private void tglBtnLockScreenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tglBtnLockScreenActionPerformed
        if( tglBtnLockScreen.isSelected())
            tglBtnLockScreen.setText( "Разблокировать экран");
        else
            tglBtnLockScreen.setText( "Заблокировать экран");
    }//GEN-LAST:event_tglBtnLockScreenActionPerformed

    private void sldPresetMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sldPresetMouseDragged
        int value = sldPreset.getValue();
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
    }//GEN-LAST:event_sldPresetMouseDragged

    private void sldPresetMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sldPresetMouseReleased
        int value = sldPreset.getValue();
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
    }//GEN-LAST:event_sldPresetMouseReleased

    private void sldPresetMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_sldPresetMouseWheelMoved
        
    }//GEN-LAST:event_sldPresetMouseWheelMoved

    private void btnPresetUpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPresetUpActionPerformed
        int value = sldPreset.getValue();
        value += 10;
        if( value < 0) value = 0;
        if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
        sldPreset.setValue( value);
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
    }//GEN-LAST:event_btnPresetUpActionPerformed

    private void btnPresetDownActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPresetDownActionPerformed
        int value = sldPreset.getValue();
        value -= 10;
        if( value < 0) value = 0;
        if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
        sldPreset.setValue( value);
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
    }//GEN-LAST:event_btnPresetDownActionPerformed

    private void edtPresetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edtPresetActionPerformed
        String strEdt = edtPreset.getText();
        int value;
        try {
            value = Integer.parseInt(strEdt);
            if( value < 0) value = 0;
            if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
            value = sldPreset.getValue();
            edtPreset.setText( "" + value);
        }
    }//GEN-LAST:event_edtPresetActionPerformed

    private void rad1AOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad1AOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "1A");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "1A: OFF: queued");
        }
    }//GEN-LAST:event_rad1AOffActionPerformed

    private void rad1TOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad1TOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "1T");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "1T: OFF: queued");
        }
    }//GEN-LAST:event_rad1TOffActionPerformed

    private void rad2AOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad2AOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "2A");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "2A: OFF: queued");
        }
    }//GEN-LAST:event_rad2AOffActionPerformed

    private void rad2TOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad2TOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "2T");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "2T: OFF: queued");
        }
    }//GEN-LAST:event_rad2TOffActionPerformed

    private void rad3AOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad3AOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "3A");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "3A: OFF: queued");
        }
    }//GEN-LAST:event_rad3AOffActionPerformed

    private void rad3TOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad3TOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "3T");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "3T: OFF: queued");
        }
    }//GEN-LAST:event_rad3TOffActionPerformed

    private void rad4AOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad4AOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "4A");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "4A: OFF: queued");
        }
    }//GEN-LAST:event_rad4AOffActionPerformed

    private void rad4TOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rad4TOffActionPerformed
        ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( "4T");
        if( q != null) {
            byte aBytes[] = new byte[1];
            aBytes[0] = 0x03;
            q.add( aBytes);
            logger.info( "4T: OFF: queued");
        }
    }//GEN-LAST:event_rad4TOffActionPerformed

    private void lblEmergencyOffMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblEmergencyOffMouseClicked
        m_nEmergencyOffClicks++;
        tEmergencyOffClicksDrop.restart();
        if( m_nEmergencyOffClicks == 3) {
            lblEmergencyOff.setBackground( new Color( 0, 250, 0));
            rad1AOff.setEnabled( true); rad1AOff.doClick();
            rad1TOff.setEnabled( true); rad1TOff.doClick();
            rad2AOff.setEnabled( true); rad2AOff.doClick();
            rad2TOff.setEnabled( true); rad2TOff.doClick();
            rad3AOff.setEnabled( true); rad3AOff.doClick();
            rad3TOff.setEnabled( true); rad3TOff.doClick();
            rad4AOff.setEnabled( true); rad4AOff.doClick();
            rad4TOff.setEnabled( true); rad4TOff.doClick();
            tEmergencyOffClicksDrop.stop();
        }

        switch( m_nEmergencyOffClicks) {
            case 1: lblEmergencyOff.setBackground( new Color( 150, 0, 0)); break;
            case 2: lblEmergencyOff.setBackground( new Color( 250, 0, 0)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        lblEmergencyOff.setBackground( null);
                    }
                }).start();
                m_nEmergencyOffClicks = 0;
                break;
            default: lblEmergencyOff.setBackground( null); break;
        }
    }//GEN-LAST:event_lblEmergencyOffMouseClicked

    private void btnPresetApplyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPresetApplyActionPerformed
        String strEdt = edtPreset.getText();
        int value;
        try {
            value = Integer.parseInt(strEdt);
            if( value < 0) value = 0;
            if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
            return;
        }        
        
        /*
        Set set = theApp.m_mapSerials.entrySet();
        Iterator it = set.iterator();
        while( it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();

            String strId = ( String) entry.getKey();
            ConcurrentLinkedQueue q = ( ConcurrentLinkedQueue) theApp.m_mapCommands.get( strId);
            
            boolean bApply = false;
            switch( strId) {
                case "1A": if( rad1AOn.isSelected()) bApply = true; break;
                case "1T": if( rad1TOn.isSelected()) bApply = true; break;
                case "2A": if( rad2AOn.isSelected()) bApply = true; break;
                case "2T": if( rad2TOn.isSelected()) bApply = true; break;
                case "3A": if( rad3AOn.isSelected()) bApply = true; break;
                case "3T": if( rad3TOn.isSelected()) bApply = true; break;
                case "4A": if( rad4AOn.isSelected()) bApply = true; break;
                case "4T": if( rad4TOn.isSelected()) bApply = true; break;
            }

            if( bApply) {
                byte aBytes[] = new byte[1];
                aBytes[0] = 0x02;
                q.add( aBytes);
                logger.info( strId + ": ON/UDATE: queued");
            }
            
        }
        */
    }//GEN-LAST:event_btnPresetApplyActionPerformed

    private void btnTurnOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTurnOffActionPerformed
        if( rad1AOn.isSelected()) rad1AOff.doClick();
        if( rad1TOn.isSelected()) rad1TOff.doClick();
        if( rad2AOn.isSelected()) rad2AOff.doClick();
        if( rad2TOn.isSelected()) rad2TOff.doClick();
        if( rad3AOn.isSelected()) rad3AOff.doClick();
        if( rad3TOn.isSelected()) rad3TOff.doClick();
        if( rad4AOn.isSelected()) rad4AOff.doClick();
        if( rad4TOn.isSelected()) rad4TOff.doClick();
    }//GEN-LAST:event_btnTurnOffActionPerformed

    public void Reconnect( String strIdentifier) {
        
        logger.info( "Переподключение " + strIdentifier + ": Останавливаем поток-обработчик");
        HVV4_HV_StreamProcessingThread pid = ( HVV4_HV_StreamProcessingThread) theApp.m_mapProcessorsRunnables.get( strIdentifier);
        if( pid != null) {
            boolean bStopped = true;
            do {
                logger.info( "Переподключение " + strIdentifier + ":  Waiting for processor thread " + strIdentifier);
                pid.m_bStopThread = true;
                Thread thread = ( Thread) theApp.m_mapProcessorsThreads.get( strIdentifier);
                try {
                    thread.join( 1000);
                    bStopped = false;
                } catch (InterruptedException ex) {
                    logger.error( "Переподключение " + strIdentifier + ": Не могу остановить поток " + strIdentifier, ex);
                }

            } while( bStopped);

            logger.info( "Переподключение " + strIdentifier + ": Waiting for processor thread " + strIdentifier + " ... [OK]");
        }
        
        logger.info( "Переподключение " + strIdentifier + ": Закрываем порт");
        SerialPort serialPort = null;
        serialPort = ( SerialPort) theApp.m_mapSerials.get( strIdentifier);
        if( serialPort != null) {
            logger.info( "Переподключение " + strIdentifier + ":  Closing port " + serialPort.getPortName());
            try {
                serialPort.removeEventListener();
                serialPort.closePort();
            }
            catch( SerialPortException ex) {
                if( serialPort != null)
                    logger.error( "Переподключение " + strIdentifier + ": При попытке закрытия " + serialPort.getPortName() + " получили исключительную ситуацию:\n\n", ex);
                else
                    logger.error( "Переподключение " + strIdentifier + ": При попытке закрытия [НЕПОНЯТНО ЧЕГО] получили исключительную ситуацию:\n\n", ex);
            }
        }
        
        
    
        HVV4_HV_CircleBuffer cBuffer;
        HVV4_HvApp.PortReader evListener;
        serialPort = new SerialPort( theApp.GetSettings().GetPort( strIdentifier));
        try {
            //Открываем порт
            serialPort.openPort();

            //Выставляем параметры
            serialPort.setParams( 38400,
                                 SerialPort.DATABITS_8,
                                 0,//SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            //Устанавливаем ивент лисенер и маску
            evListener = theApp.createPortReader( strIdentifier);
            serialPort.addEventListener( evListener, SerialPort.MASK_RXCHAR);
            
            //создаём кольцевой буфер для этого канала связи
            cBuffer = new HVV4_HV_CircleBuffer();
            
            //создаем и запускаем поток обрабатывающий входящие данные из этого канала связи
            HVV4_HV_StreamProcessingThread processor = new HVV4_HV_StreamProcessingThread( theApp, strIdentifier);
            Thread thread = new Thread( processor);
            thread.start();
            
            //создаём объект очереди исходящих команд для этого канала связи
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();

            theApp.m_mapCalibrationP.put( strIdentifier, new HVV4_HvCalibration(
                            theApp.GetAMSRoot() + File.separator +
                            "etc" + File.separator +
                            "calibration" + File.separator +
                            "HVV4.HV." + strIdentifier + ".calib.P.xml", "CalibrationP"));                            
            theApp.m_mapCalibrationI.put( strIdentifier, new HVV4_HvCalibration(
                            theApp.GetAMSRoot() + File.separator +
                            "etc" + File.separator +
                            "calibration" + File.separator +
                            "HVV4.HV." + strIdentifier + ".calib.I.xml", "CalibrationI"));
            theApp.m_mapCalibrationU.put( strIdentifier, new HVV4_HvCalibration(
                            theApp.GetAMSRoot() + File.separator +
                            "etc" + File.separator +
                            "calibration" + File.separator +
                            "HVV4.HV." + strIdentifier + ".calib.U.xml", "CalibrationU"));
            theApp.m_mapSerials.put( strIdentifier, serialPort);
            theApp.m_mapSerialListeners.put( strIdentifier, evListener);
            theApp.m_mapCircleBuffers.put( strIdentifier, cBuffer);
            theApp.m_mapProcessorsRunnables.put( strIdentifier, processor);
            theApp.m_mapProcessorsThreads.put( strIdentifier, thread);
            theApp.m_mapCommands.put( strIdentifier, q);
        }
        catch( SerialPortException ex) {
            logger.error( "Переподключение " + strIdentifier + ": При попытке соединения c " + theApp.GetSettings().GetPort( strIdentifier) + " получили исключительную ситуацию:\n\n", ex);
        }
    }
    
    private void formMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_formMouseWheelMoved
        int value = sldPreset.getValue();
        value -= evt.getWheelRotation() * 100;
        if( value < 0) value = 0;
        if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
        sldPreset.setValue( value);
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
    }//GEN-LAST:event_formMouseWheelMoved

    private void btnPreset1100ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset1100ActionPerformed
        int value = 1100;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset1100ActionPerformed

    private void btnPreset500ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset500ActionPerformed
        int value = 500;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset500ActionPerformed

    private void btnPreset600ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset600ActionPerformed
        int value = 600;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset600ActionPerformed

    private void btnPreset1000ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset1000ActionPerformed
        int value = 1000;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset1000ActionPerformed

    private void lbl1TMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl1TMouseClicked
        JLabel lbl = lbl1T;
        m_strReconnectElectrode = "1T";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "1T");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl1TMouseClicked

    private void lbl2TMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl2TMouseClicked
        JLabel lbl = lbl2T;
        m_strReconnectElectrode = "2T";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "2T");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl2TMouseClicked

    private void lbl3TMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl3TMouseClicked
        JLabel lbl = lbl3T;
        m_strReconnectElectrode = "3T";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "3T");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl3TMouseClicked

    private void lbl4TMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl4TMouseClicked
        JLabel lbl = lbl4T;
        m_strReconnectElectrode = "4T";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "4T");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl4TMouseClicked

    private void lbl1AMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl1AMouseClicked
        JLabel lbl = lbl1A;
        m_strReconnectElectrode = "1A";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "1A");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl1AMouseClicked

    private void lbl3AMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl3AMouseClicked
        JLabel lbl = lbl3A;
        m_strReconnectElectrode = "3A";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "3A");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl3AMouseClicked

    private void lbl4AMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl4AMouseClicked
        JLabel lbl = lbl4A;
        m_strReconnectElectrode = "4A";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "4A");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl4AMouseClicked

    private void lbl2AMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl2AMouseClicked
        JLabel lbl = lbl2A;
        m_strReconnectElectrode = "2A";
        m_nReconnectClicks++;
        tReconnectClicksDrop.restart();
        if( m_nReconnectClicks == 3) {
            lbl.setBackground( new Color( 100, 250, 80));
            Reconnect( "2A");
            tReconnectClicksDrop.stop();
        }

        switch( m_nReconnectClicks) {
            case 1: lbl.setBackground( new Color( 180,  80, 80)); break;
            case 2: lbl.setBackground( new Color( 180, 180, 80)); break;
            case 3:
                
                new Timer( 5000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        if( m_strReconnectElectrode != null) {
                            switch( m_strReconnectElectrode) {
                                case "1A": lbl1A.setBackground( null); break;
                                case "1T": lbl1T.setBackground( null); break;
                                case "2A": lbl2A.setBackground( null); break;
                                case "2T": lbl2T.setBackground( null); break;
                                case "3A": lbl3A.setBackground( null); break;
                                case "3T": lbl3T.setBackground( null); break;
                                case "4A": lbl4A.setBackground( null); break;
                                case "4T": lbl4T.setBackground( null); break;
                            }
                        }
                    }
                }).start();
                m_nReconnectClicks = 0;
                break;
            default: lbl.setBackground( null); break;
        }
    }//GEN-LAST:event_lbl2AMouseClicked

    private void btnPreset1200ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset1200ActionPerformed
        int value = 1200;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset1200ActionPerformed

    private void btnPreset2500ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset2500ActionPerformed
        int value = 2500;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset2500ActionPerformed

    private void btnPreset3500ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPreset3500ActionPerformed
        int value = 3500;
        try {
            sldPreset.setValue( value);
            edtPreset.setText( "" + value);
            tApplyPreset.restart();
        } catch (NumberFormatException nfe) {
            logger.warn( nfe);
        }
    }//GEN-LAST:event_btnPreset3500ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new HVV4_HvMainFrame( null).setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExit;
    private javax.swing.ButtonGroup btnGroup1A;
    private javax.swing.ButtonGroup btnGroup1T;
    private javax.swing.ButtonGroup btnGroup2A;
    private javax.swing.ButtonGroup btnGroup2T;
    private javax.swing.ButtonGroup btnGroup3A;
    private javax.swing.ButtonGroup btnGroup3T;
    private javax.swing.ButtonGroup btnGroup4A;
    private javax.swing.ButtonGroup btnGroup4T;
    private javax.swing.JButton btnPreset1000;
    private javax.swing.JButton btnPreset1100;
    private javax.swing.JButton btnPreset1200;
    private javax.swing.JButton btnPreset2500;
    private javax.swing.JButton btnPreset3500;
    private javax.swing.JButton btnPreset500;
    private javax.swing.JButton btnPreset600;
    private javax.swing.JButton btnPresetApply;
    private javax.swing.JButton btnPresetDown;
    private javax.swing.JButton btnPresetUp;
    private javax.swing.JButton btnTurnOff;
    private javax.swing.JTextField edtPreset;
    private javax.swing.JLabel lbl1A;
    private javax.swing.JLabel lbl1T;
    private javax.swing.JLabel lbl2A;
    private javax.swing.JLabel lbl2T;
    private javax.swing.JLabel lbl3A;
    private javax.swing.JLabel lbl3T;
    private javax.swing.JLabel lbl4A;
    private javax.swing.JLabel lbl4T;
    private javax.swing.JLabel lblCurrentATitle;
    private javax.swing.JLabel lblCurrentTTitle;
    private javax.swing.JLabel lblEmergencyOff;
    private javax.swing.JLabel lblI1A;
    private javax.swing.JLabel lblI1T;
    private javax.swing.JLabel lblI2A;
    private javax.swing.JLabel lblI2T;
    private javax.swing.JLabel lblI3A;
    private javax.swing.JLabel lblI3T;
    private javax.swing.JLabel lblI4A;
    private javax.swing.JLabel lblI4T;
    private javax.swing.JLabel lblSetCurrentTitle;
    private javax.swing.JLabel lblU1A;
    private javax.swing.JLabel lblU1T;
    private javax.swing.JLabel lblU2A;
    private javax.swing.JLabel lblU2T;
    private javax.swing.JLabel lblU3A;
    private javax.swing.JLabel lblU3T;
    private javax.swing.JLabel lblU4A;
    private javax.swing.JLabel lblU4T;
    private javax.swing.JLabel lblVoltageATitle;
    private javax.swing.JLabel lblVoltageTTitle;
    private javax.swing.JRadioButton rad1AOff;
    private javax.swing.JRadioButton rad1AOn;
    private javax.swing.JRadioButton rad1AStay;
    private javax.swing.JRadioButton rad1TOff;
    private javax.swing.JRadioButton rad1TOn;
    private javax.swing.JRadioButton rad1TStay;
    private javax.swing.JRadioButton rad2AOff;
    private javax.swing.JRadioButton rad2AOn;
    private javax.swing.JRadioButton rad2AStay;
    private javax.swing.JRadioButton rad2TOff;
    private javax.swing.JRadioButton rad2TOn;
    private javax.swing.JRadioButton rad2TStay;
    private javax.swing.JRadioButton rad3AOff;
    private javax.swing.JRadioButton rad3AOn;
    private javax.swing.JRadioButton rad3AStay;
    private javax.swing.JRadioButton rad3TOff;
    private javax.swing.JRadioButton rad3TOn;
    private javax.swing.JRadioButton rad3TStay;
    private javax.swing.JRadioButton rad4AOff;
    private javax.swing.JRadioButton rad4AOn;
    private javax.swing.JRadioButton rad4AStay;
    private javax.swing.JRadioButton rad4TOff;
    private javax.swing.JRadioButton rad4TOn;
    private javax.swing.JRadioButton rad4TStay;
    private javax.swing.JSlider sldPreset;
    private javax.swing.JToggleButton tglBtnLockScreen;
    // End of variables declaration//GEN-END:variables
}
