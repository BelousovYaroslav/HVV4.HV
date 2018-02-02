/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

import hvv4.hv.comm.HVV4_HV_StreamProcessingThread;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    Timer tPolling;
    Timer tCommandSend;
    Timer tApplyPreset;
    
    int m_nEmergencyOffClicks;
    Timer tEmergencyOffClicksDrop;
    /**
     * Creates new form HVV4HVMainFrame
     */
    public HVV4_HvMainFrame( HVV4_HvApp app) {
        initComponents();
        theApp = app;
        
        m_nEmergencyOffClicks = 0;
        tEmergencyOffClicksDrop = new Timer( 500, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                m_nEmergencyOffClicks = 0;
                tEmergencyOffClicksDrop.stop();
                lblEmergencyOff.setBackground( null);
                logger.fatal( "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            }
        });
                
        setTitle( "HVV4.Модуль управления  в\\в. (2018.02.02 13:00), (C) ФЛАВТ 2018.");
        
        //таймер, отправляющий команды выставки уставки в контроллеры в/в модулей
        tApplyPreset = new Timer( 300, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                tApplyPreset.stop();
                int value = sldPreset.getValue();
                int code = value;
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
                        byte aBytes[] = new byte[3];
                        aBytes[0] = 0x01;
                        aBytes[1] = ( byte) ( code & 0xFF);
                        aBytes[2] = ( byte) ( ( code & 0xFF00) >> 8);
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
                                logger.info( strId + ": SEND CMD: (" + strCmd + ") sent");
                            } catch (SerialPortException ex) {
                                logger.error( strId + ": SEND CMD: FAIL: COM-Communication exception", ex);
                            }
                        }
                        else
                            logger.debug( strId + ": SEND CMD: EMPTY");
                    }
                    else {
                        logger.warn( strId + ": SEND CMD: REJECTED");
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
                
                if( theApp.m_mapU.containsKey( "1A")) { nVal = ( int) theApp.m_mapU.get( "1A"); strU1A = String.format( "%.0f", ( double) nVal / 32.); }
                if( theApp.m_mapU.containsKey( "2A")) { nVal = ( int) theApp.m_mapU.get( "2A"); strU2A = "" + nVal; }
                if( theApp.m_mapU.containsKey( "3A")) { nVal = ( int) theApp.m_mapU.get( "3A"); strU3A = "" + nVal; }
                if( theApp.m_mapU.containsKey( "4A")) { nVal = ( int) theApp.m_mapU.get( "4A"); strU4A = "" + nVal; }
                
                if( theApp.m_mapU.containsKey( "1T")) { nVal = ( int) theApp.m_mapU.get( "1T"); strU1T = "" + nVal; }
                if( theApp.m_mapU.containsKey( "2T")) { nVal = ( int) theApp.m_mapU.get( "2T"); strU2T = "" + nVal; }
                if( theApp.m_mapU.containsKey( "3T")) { nVal = ( int) theApp.m_mapU.get( "3T"); strU3T = "" + nVal; }
                if( theApp.m_mapU.containsKey( "4T")) { nVal = ( int) theApp.m_mapU.get( "4T"); strU4T = "" + nVal; }
                
                if( theApp.m_mapI.containsKey( "1A")) { nVal = ( int) theApp.m_mapI.get( "1A"); strI1A = String.format( "%.0f", ( double) nVal * 4096. / 65535. * 5.); }
                if( theApp.m_mapI.containsKey( "2A")) { nVal = ( int) theApp.m_mapI.get( "2A"); strI2A = "" + nVal; }
                if( theApp.m_mapI.containsKey( "3A")) { nVal = ( int) theApp.m_mapI.get( "3A"); strI3A = "" + nVal; }
                if( theApp.m_mapI.containsKey( "4A")) { nVal = ( int) theApp.m_mapI.get( "4A"); strI4A = "" + nVal; }
                
                if( theApp.m_mapI.containsKey( "1T")) { nVal = ( int) theApp.m_mapI.get( "1T"); strI1T = "" + nVal; }
                if( theApp.m_mapI.containsKey( "2T")) { nVal = ( int) theApp.m_mapI.get( "2T"); strI2T = "" + nVal; }
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
                        logger.info( strId + ": POLLING: queued");
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
        lbl1ATitle = new javax.swing.JLabel();
        rad2AOn = new javax.swing.JRadioButton();
        rad2AOff = new javax.swing.JRadioButton();
        rad2AStay = new javax.swing.JRadioButton();
        lbl2ATitle = new javax.swing.JLabel();
        rad3AOn = new javax.swing.JRadioButton();
        rad3AOff = new javax.swing.JRadioButton();
        rad3AStay = new javax.swing.JRadioButton();
        lbl3ATitle = new javax.swing.JLabel();
        rad4AOn = new javax.swing.JRadioButton();
        rad4AOff = new javax.swing.JRadioButton();
        rad4AStay = new javax.swing.JRadioButton();
        lbl4ATitle = new javax.swing.JLabel();
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
        lbl1TTitle = new javax.swing.JLabel();
        rad2TOn = new javax.swing.JRadioButton();
        rad2TOff = new javax.swing.JRadioButton();
        rad2TStay = new javax.swing.JRadioButton();
        lbl2TTitle = new javax.swing.JLabel();
        rad3TOn = new javax.swing.JRadioButton();
        rad3TOff = new javax.swing.JRadioButton();
        rad3TStay = new javax.swing.JRadioButton();
        lbl3TTitle = new javax.swing.JLabel();
        rad4TOn = new javax.swing.JRadioButton();
        rad4TOff = new javax.swing.JRadioButton();
        rad4TStay = new javax.swing.JRadioButton();
        lbl4TTitle = new javax.swing.JLabel();
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

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(580, 540));
        setResizable(false);
        getContentPane().setLayout(null);

        lblSetCurrentTitle.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        lblSetCurrentTitle.setText("Уставка выходного тока, мкА:");
        getContentPane().add(lblSetCurrentTitle);
        lblSetCurrentTitle.setBounds(10, 10, 460, 40);

        edtPreset.setFont(new java.awt.Font("Dialog", 0, 32)); // NOI18N
        edtPreset.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        edtPreset.setText("1000");
        edtPreset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edtPresetActionPerformed(evt);
            }
        });
        getContentPane().add(edtPreset);
        edtPreset.setBounds(410, 10, 110, 40);

        btnPresetDown.setIcon(new javax.swing.ImageIcon("/home/yaroslav/HVV_HOME/res/images/down.gif")); // NOI18N
        btnPresetDown.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetDownActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetDown);
        btnPresetDown.setBounds(520, 30, 40, 20);

        btnPresetUp.setIcon(new javax.swing.ImageIcon("/home/yaroslav/HVV_HOME/res/images/up.gif")); // NOI18N
        btnPresetUp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetUpActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetUp);
        btnPresetUp.setBounds(520, 10, 40, 20);

        sldPreset.setMaximum(3000);
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
        sldPreset.setBounds(10, 40, 560, 40);

        btnPresetApply.setText("Подать / обновить");
        btnPresetApply.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPresetApplyActionPerformed(evt);
            }
        });
        getContentPane().add(btnPresetApply);
        btnPresetApply.setBounds(10, 90, 310, 40);

        btnTurnOff.setText("Снять");
        btnTurnOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTurnOffActionPerformed(evt);
            }
        });
        getContentPane().add(btnTurnOff);
        btnTurnOff.setBounds(420, 90, 150, 40);

        btnGroup1A.add(rad1AOn);
        rad1AOn.setText("Вкл.");
        getContentPane().add(rad1AOn);
        rad1AOn.setBounds(60, 130, 70, 20);

        btnGroup1A.add(rad1AOff);
        rad1AOff.setSelected(true);
        rad1AOff.setText("Выкл.");
        rad1AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad1AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad1AOff);
        rad1AOff.setBounds(60, 150, 70, 20);

        btnGroup1A.add(rad1AStay);
        rad1AStay.setText("Ост.");
        getContentPane().add(rad1AStay);
        rad1AStay.setBounds(60, 170, 70, 20);

        lbl1ATitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl1ATitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl1ATitle.setText("1А");
        getContentPane().add(lbl1ATitle);
        lbl1ATitle.setBounds(130, 130, 50, 60);

        btnGroup2A.add(rad2AOn);
        rad2AOn.setText("Вкл.");
        getContentPane().add(rad2AOn);
        rad2AOn.setBounds(190, 130, 70, 20);

        btnGroup2A.add(rad2AOff);
        rad2AOff.setSelected(true);
        rad2AOff.setText("Выкл.");
        rad2AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad2AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad2AOff);
        rad2AOff.setBounds(190, 150, 70, 20);

        btnGroup2A.add(rad2AStay);
        rad2AStay.setText("Ост.");
        getContentPane().add(rad2AStay);
        rad2AStay.setBounds(190, 170, 70, 20);

        lbl2ATitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl2ATitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl2ATitle.setText("2А");
        getContentPane().add(lbl2ATitle);
        lbl2ATitle.setBounds(260, 130, 50, 60);

        btnGroup3A.add(rad3AOn);
        rad3AOn.setText("Вкл.");
        getContentPane().add(rad3AOn);
        rad3AOn.setBounds(320, 130, 70, 20);

        btnGroup3A.add(rad3AOff);
        rad3AOff.setSelected(true);
        rad3AOff.setText("Выкл.");
        rad3AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad3AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad3AOff);
        rad3AOff.setBounds(320, 150, 70, 20);

        btnGroup3A.add(rad3AStay);
        rad3AStay.setText("Ост.");
        getContentPane().add(rad3AStay);
        rad3AStay.setBounds(320, 170, 70, 20);

        lbl3ATitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl3ATitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl3ATitle.setText("3А");
        getContentPane().add(lbl3ATitle);
        lbl3ATitle.setBounds(390, 130, 50, 60);

        btnGroup4A.add(rad4AOn);
        rad4AOn.setText("Вкл.");
        getContentPane().add(rad4AOn);
        rad4AOn.setBounds(450, 130, 70, 20);

        btnGroup4A.add(rad4AOff);
        rad4AOff.setSelected(true);
        rad4AOff.setText("Выкл.");
        rad4AOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad4AOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad4AOff);
        rad4AOff.setBounds(450, 150, 70, 20);

        btnGroup4A.add(rad4AStay);
        rad4AStay.setText("Ост.");
        getContentPane().add(rad4AStay);
        rad4AStay.setBounds(450, 170, 70, 20);

        lbl4ATitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl4ATitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl4ATitle.setText("4А");
        getContentPane().add(lbl4ATitle);
        lbl4ATitle.setBounds(520, 130, 50, 60);

        lblVoltageATitle.setText("U, В");
        getContentPane().add(lblVoltageATitle);
        lblVoltageATitle.setBounds(10, 190, 50, 50);

        lblU1A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU1A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU1A.setText("1000");
        lblU1A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU1A);
        lblU1A.setBounds(60, 190, 120, 50);

        lblU2A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU2A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU2A.setText("1000");
        lblU2A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU2A);
        lblU2A.setBounds(190, 190, 120, 50);

        lblU3A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU3A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU3A.setText("1000");
        lblU3A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU3A);
        lblU3A.setBounds(320, 190, 120, 50);

        lblU4A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU4A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU4A.setText("1000");
        lblU4A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU4A);
        lblU4A.setBounds(450, 190, 120, 50);

        lblCurrentATitle.setText("I, мкА");
        getContentPane().add(lblCurrentATitle);
        lblCurrentATitle.setBounds(10, 240, 50, 50);

        lblI1A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI1A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI1A.setText("1000");
        lblI1A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI1A);
        lblI1A.setBounds(60, 240, 120, 50);

        lblI2A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI2A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI2A.setText("1000");
        lblI2A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI2A);
        lblI2A.setBounds(190, 240, 120, 50);

        lblI3A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI3A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI3A.setText("1000");
        lblI3A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI3A);
        lblI3A.setBounds(320, 240, 120, 50);

        lblI4A.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI4A.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI4A.setText("1000");
        lblI4A.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI4A);
        lblI4A.setBounds(450, 240, 120, 50);

        btnGroup1T.add(rad1TOn);
        rad1TOn.setText("Вкл.");
        getContentPane().add(rad1TOn);
        rad1TOn.setBounds(60, 320, 70, 20);

        btnGroup1T.add(rad1TOff);
        rad1TOff.setSelected(true);
        rad1TOff.setText("Выкл.");
        rad1TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad1TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad1TOff);
        rad1TOff.setBounds(60, 340, 70, 20);

        btnGroup1T.add(rad1TStay);
        rad1TStay.setText("Ост.");
        getContentPane().add(rad1TStay);
        rad1TStay.setBounds(60, 360, 70, 20);

        lbl1TTitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl1TTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl1TTitle.setText("1Ш");
        getContentPane().add(lbl1TTitle);
        lbl1TTitle.setBounds(130, 320, 50, 60);

        btnGroup2T.add(rad2TOn);
        rad2TOn.setText("Вкл.");
        getContentPane().add(rad2TOn);
        rad2TOn.setBounds(190, 320, 70, 20);

        btnGroup2T.add(rad2TOff);
        rad2TOff.setSelected(true);
        rad2TOff.setText("Выкл.");
        rad2TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad2TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad2TOff);
        rad2TOff.setBounds(190, 340, 70, 20);

        btnGroup2T.add(rad2TStay);
        rad2TStay.setText("Ост.");
        getContentPane().add(rad2TStay);
        rad2TStay.setBounds(190, 360, 70, 20);

        lbl2TTitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl2TTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl2TTitle.setText("2Ш");
        getContentPane().add(lbl2TTitle);
        lbl2TTitle.setBounds(260, 320, 50, 60);

        btnGroup3T.add(rad3TOn);
        rad3TOn.setText("Вкл.");
        getContentPane().add(rad3TOn);
        rad3TOn.setBounds(320, 320, 70, 20);

        btnGroup3T.add(rad3TOff);
        rad3TOff.setSelected(true);
        rad3TOff.setText("Выкл.");
        rad3TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad3TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad3TOff);
        rad3TOff.setBounds(320, 340, 70, 20);

        btnGroup3T.add(rad3TStay);
        rad3TStay.setText("Ост.");
        getContentPane().add(rad3TStay);
        rad3TStay.setBounds(320, 360, 70, 20);

        lbl3TTitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl3TTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl3TTitle.setText("3Ш");
        getContentPane().add(lbl3TTitle);
        lbl3TTitle.setBounds(390, 320, 50, 60);

        btnGroup4T.add(rad4TOn);
        rad4TOn.setText("Вкл.");
        getContentPane().add(rad4TOn);
        rad4TOn.setBounds(450, 320, 70, 20);

        btnGroup4T.add(rad4TOff);
        rad4TOff.setSelected(true);
        rad4TOff.setText("Выкл.");
        rad4TOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rad4TOffActionPerformed(evt);
            }
        });
        getContentPane().add(rad4TOff);
        rad4TOff.setBounds(450, 340, 70, 20);

        btnGroup4T.add(rad4TStay);
        rad4TStay.setText("Ост.");
        getContentPane().add(rad4TStay);
        rad4TStay.setBounds(450, 360, 70, 20);

        lbl4TTitle.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        lbl4TTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl4TTitle.setText("4Ш");
        getContentPane().add(lbl4TTitle);
        lbl4TTitle.setBounds(520, 320, 50, 60);

        lblVoltageTTitle.setText("U, В");
        getContentPane().add(lblVoltageTTitle);
        lblVoltageTTitle.setBounds(10, 380, 50, 50);

        lblU1T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU1T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU1T.setText("1000");
        lblU1T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU1T);
        lblU1T.setBounds(60, 380, 120, 50);

        lblU2T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU2T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU2T.setText("1000");
        lblU2T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU2T);
        lblU2T.setBounds(190, 380, 120, 50);

        lblU3T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU3T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU3T.setText("1000");
        lblU3T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU3T);
        lblU3T.setBounds(320, 380, 120, 50);

        lblU4T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblU4T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblU4T.setText("1000");
        lblU4T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblU4T);
        lblU4T.setBounds(450, 380, 120, 50);

        lblCurrentTTitle.setText("I, мкА");
        getContentPane().add(lblCurrentTTitle);
        lblCurrentTTitle.setBounds(10, 430, 50, 50);

        lblI1T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI1T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI1T.setText("1000");
        lblI1T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI1T);
        lblI1T.setBounds(60, 430, 120, 50);

        lblI2T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI2T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI2T.setText("1000");
        lblI2T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI2T);
        lblI2T.setBounds(190, 430, 120, 50);

        lblI3T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI3T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI3T.setText("1000");
        lblI3T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI3T);
        lblI3T.setBounds(320, 430, 120, 50);

        lblI4T.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblI4T.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblI4T.setText("1000");
        lblI4T.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        getContentPane().add(lblI4T);
        lblI4T.setBounds(450, 430, 120, 50);

        tglBtnLockScreen.setText("Заблокировать экран");
        tglBtnLockScreen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tglBtnLockScreenActionPerformed(evt);
            }
        });
        getContentPane().add(tglBtnLockScreen);
        tglBtnLockScreen.setBounds(10, 490, 200, 40);

        lblEmergencyOff.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblEmergencyOff.setText("<html>АВАРИЙНОЕ ВЫКЛЮЧЕНИЕ<br>ТРОЙНОЙ КЛИК СЮДА!</html>");
        lblEmergencyOff.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        lblEmergencyOff.setOpaque(true);
        lblEmergencyOff.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lblEmergencyOffMouseClicked(evt);
            }
        });
        getContentPane().add(lblEmergencyOff);
        lblEmergencyOff.setBounds(220, 490, 200, 40);

        btnExit.setText("Выход");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });
        getContentPane().add(btnExit);
        btnExit.setBounds(430, 490, 140, 40);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExitActionPerformed
        tRefreshValues.stop();
        tPolling.stop();
        tCommandSend.stop();
        theApp.ClosePorts();
        this.dispose();
    }//GEN-LAST:event_btnExitActionPerformed

    private void tglBtnLockScreenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tglBtnLockScreenActionPerformed
        if( tglBtnLockScreen.isSelected()) {
            tglBtnLockScreen.setText( "Разблокировать экран");
            
            edtPreset.setEnabled( false);
            btnPresetUp.setEnabled( false);
            btnPresetDown.setEnabled( false);
            sldPreset.setEnabled( false);
            btnPresetApply.setEnabled( false);
            btnTurnOff.setEnabled( false);
            
            rad1AOff.setEnabled( false);
            rad1AOn.setEnabled( false);
            rad1AStay.setEnabled( false);
            rad2AOff.setEnabled( false);
            rad2AOn.setEnabled( false);
            rad2AStay.setEnabled( false);
            rad3AOff.setEnabled( false);
            rad3AOn.setEnabled( false);
            rad3AStay.setEnabled( false);
            rad4AOff.setEnabled( false);
            rad4AOn.setEnabled( false);
            rad4AStay.setEnabled( false);
            
            rad1TOff.setEnabled( false);
            rad1TOn.setEnabled( false);
            rad1TStay.setEnabled( false);
            rad2TOff.setEnabled( false);
            rad2TOn.setEnabled( false);
            rad2TStay.setEnabled( false);
            rad3TOff.setEnabled( false);
            rad3TOn.setEnabled( false);
            rad3TStay.setEnabled( false);
            rad4TOff.setEnabled( false);
            rad4TOn.setEnabled( false);
            rad4TStay.setEnabled( false);
            
            btnExit.setEnabled( false);
        }
        else {
            tglBtnLockScreen.setText( "Заблокировать экран");
            
            edtPreset.setEnabled( true);
            btnPresetUp.setEnabled( true);
            btnPresetDown.setEnabled( true);
            sldPreset.setEnabled( true);
            btnPresetApply.setEnabled( true);
            btnTurnOff.setEnabled( true);
            
            rad1AOff.setEnabled( true);
            rad1AOn.setEnabled( true);
            rad1AStay.setEnabled( true);
            rad2AOff.setEnabled( true);
            rad2AOn.setEnabled( true);
            rad2AStay.setEnabled( true);
            rad3AOff.setEnabled( true);
            rad3AOn.setEnabled( true);
            rad3AStay.setEnabled( true);
            rad4AOff.setEnabled( true);
            rad4AOn.setEnabled( true);
            rad4AStay.setEnabled( true);
            
            rad1TOff.setEnabled( true);
            rad1TOn.setEnabled( true);
            rad1TStay.setEnabled( true);
            rad2TOff.setEnabled( true);
            rad2TOn.setEnabled( true);
            rad2TStay.setEnabled( true);
            rad3TOff.setEnabled( true);
            rad3TOn.setEnabled( true);
            rad3TStay.setEnabled( true);
            rad4TOff.setEnabled( true);
            rad4TOn.setEnabled( true);
            rad4TStay.setEnabled( true);
            
            btnExit.setEnabled( true);
        }
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
        int value = sldPreset.getValue();
        value -= evt.getWheelRotation() * 100;
        if( value < 0) value = 0;
        if( value > sldPreset.getMaximum()) value = sldPreset.getMaximum();
        sldPreset.setValue( value);
        edtPreset.setText( "" + value);
        tApplyPreset.restart();
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
            rad1AOff.setSelected( true); rad1TOff.setSelected( true);
            rad2AOff.setSelected( true); rad2TOff.setSelected( true);
            rad3AOff.setSelected( true); rad3TOff.setSelected( true);
            rad4AOff.setSelected( true); rad4TOff.setSelected( true);
            tEmergencyOffClicksDrop.stop();
        }

        switch( m_nEmergencyOffClicks) {
            case 1: lblEmergencyOff.setBackground( new Color( 150, 0, 0)); break;
            case 2: lblEmergencyOff.setBackground( new Color( 250, 0, 0)); break;
            case 3:
                lblEmergencyOff.setBackground( new Color( 0, 250, 0));
                new Timer( 5000, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Timer t = ( Timer) e.getSource();
                        t.stop();
                        lblEmergencyOff.setBackground( null);
                        logger.fatal( "QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ");
                    }
                }).start();
                m_nEmergencyOffClicks = 0;
                break;
            default: lblEmergencyOff.setBackground( null); break;
        }
    }//GEN-LAST:event_lblEmergencyOffMouseClicked

    private void btnPresetApplyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPresetApplyActionPerformed
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
                byte aBytes[] = new byte[1];
                aBytes[0] = 0x02;
                q.add( aBytes);
                logger.info( strId + ": ON/UDATE: queued");
            }
            
        }
    }//GEN-LAST:event_btnPresetApplyActionPerformed

    private void btnTurnOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTurnOffActionPerformed
        if( rad1AOn.isSelected()) rad1AOff.setSelected( true);
        if( rad1TOn.isSelected()) rad1TOff.setSelected( true);
        if( rad2AOn.isSelected()) rad2AOff.setSelected( true);
        if( rad2TOn.isSelected()) rad2TOff.setSelected( true);
        if( rad3AOn.isSelected()) rad3AOff.setSelected( true);
        if( rad3TOn.isSelected()) rad3TOff.setSelected( true);
        if( rad4AOn.isSelected()) rad4AOff.setSelected( true);
        if( rad4TOn.isSelected()) rad4TOff.setSelected( true);
    }//GEN-LAST:event_btnTurnOffActionPerformed

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
    private javax.swing.JButton btnPresetApply;
    private javax.swing.JButton btnPresetDown;
    private javax.swing.JButton btnPresetUp;
    private javax.swing.JButton btnTurnOff;
    private javax.swing.JTextField edtPreset;
    private javax.swing.JLabel lbl1ATitle;
    private javax.swing.JLabel lbl1TTitle;
    private javax.swing.JLabel lbl2ATitle;
    private javax.swing.JLabel lbl2TTitle;
    private javax.swing.JLabel lbl3ATitle;
    private javax.swing.JLabel lbl3TTitle;
    private javax.swing.JLabel lbl4ATitle;
    private javax.swing.JLabel lbl4TTitle;
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
