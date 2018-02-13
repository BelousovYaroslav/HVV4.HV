/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

import hvv4.hv.calibration.HVV4_HvCalibration;
import hvv4.hv.comm.HVV4_HV_CircleBuffer;
import hvv4.hv.comm.HVV4_HV_StreamProcessingThread;
import hvv_resources.HVV_Resources;
import java.io.File;
import java.net.ServerSocket;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JOptionPane;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author yaroslav
 */
public class HVV4_HvApp {

    static Logger logger = Logger.getLogger( HVV4_HvApp.class);
    
    private final String m_strAMSrootEnvVar;
    public String GetAMSRoot() { return m_strAMSrootEnvVar; }
    
    public HVV4_HvMainFrame m_pMainWnd;
    
    private final HVV_Resources m_Resources;
    public HVV_Resources GetResources() { return m_Resources;}
    
    private final HVV4_HvSettings m_Settings;
    public HVV4_HvSettings GetSettings() { return m_Settings;}
    
    private ServerSocket m_pSingleInstanceSocketServer;
    
    public final TreeMap m_mapSerials;
    
    public final TreeMap m_mapSerialListeners;
    public final TreeMap m_mapCircleBuffers;
    public final TreeMap m_mapU;
    public final TreeMap m_mapI;
    public final TreeMap m_mapProcessorsRunnables;
    public final TreeMap m_mapProcessorsThreads;
    public /*final*/ TreeMap m_mapStates;
    public final TreeMap m_mapCommands;
    public final TreeMap m_mapCalibrationP;
    public final TreeMap m_mapCalibrationI;
    public final TreeMap m_mapCalibrationU;
    
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_REQUESTED = 1;
    public static final int STATE_RESPONDED = 2;
    public static final int STATE_TIMEOUT = 3;
    public static final int STATE_FAIL = 4;
    
    public void ClosePorts() {
        logger.info( "Останавливаем потоки-обработчики");
        Set set = m_mapProcessorsRunnables.entrySet();
        Iterator it = set.iterator();
        while( it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            HVV4_HV_StreamProcessingThread pid = ( HVV4_HV_StreamProcessingThread) entry.getValue();
            String strId = pid.GetIdentifier();
            
            boolean bStopped = true;
            do {
                logger.info( ">> Waiting for processor thread " + strId);
                
                pid.m_bStopThread = true;
                Thread thread = ( Thread) m_mapProcessorsThreads.get( strId);
                
                if( thread.isAlive())
                    logger.info( strId + ": ALIVE");
                else
                    logger.info( strId + ": NOT ALIVE");
                
                try {
                    thread.join( 1000);
                    if( thread.isAlive())
                        logger.info( strId + ": ALIVE");
                    else
                        logger.info( strId + ": NOT ALIVE");
                    bStopped = false;
                    if( pid.m_bRunning)
                        bStopped = true;
                } catch (InterruptedException ex) {
                    logger.error( ">> Не могу остановить поток " + strId, ex);
                }
                
            } while( bStopped);
            
            logger.info( ">> Waiting for processor thread " + strId + " ... [OK]");
        }
        
        logger.info( "Закрываем порты");
        SerialPort serialPort = null;
        
        set = m_mapSerials.entrySet();
        it = set.iterator();
        while( it.hasNext()) {
            try {
                Map.Entry entry = (Map.Entry) it.next();
                serialPort = ( SerialPort) entry.getValue();
                logger.info( ">> Closing port " + serialPort.getPortName());
                serialPort.removeEventListener();
                serialPort.closePort();
            }
            catch( SerialPortException ex) {
                if( serialPort != null)
                    logger.error( ">> При попытке закрытия " + serialPort.getPortName() + " получили исключительную ситуацию:\n\n", ex);
                else
                    logger.error( ">> При попытке закрытия [НЕПОНЯТНО ЧЕГО] получили исключительную ситуацию:\n\n", ex);
            }
        }
        
        
    }
    
    public void OpenPort( String strPort, String strIdentifier) {
        HVV4_HV_CircleBuffer cBuffer;
        SerialPort serialPort;
        PortReader evListener;
        
        logger.info( strIdentifier + ": OPENING " + strPort);
        serialPort = new SerialPort( strPort);
        try {
            //Открываем порт
            serialPort.openPort();

            //Выставляем параметры
            serialPort.setParams( 38400,
                                 SerialPort.DATABITS_8,
                                 0,//SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            //Включаем аппаратное управление потоком
            //serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | 
            //                              SerialPort.FLOWCONTROL_RTSCTS_OUT);

            //Устанавливаем ивент лисенер и маску
            evListener = new PortReader( this, strIdentifier);
            serialPort.addEventListener( evListener, SerialPort.MASK_RXCHAR);
            
            //создаём кольцевой буфер для этого канала связи
            cBuffer = new HVV4_HV_CircleBuffer();
            
            //создаем и запускаем поток обрабатывающий входящие данные из этого канала связи
            HVV4_HV_StreamProcessingThread processor = new HVV4_HV_StreamProcessingThread( this, strIdentifier);
            Thread thread = new Thread( processor);
            thread.start();
            
            //создаём объект очереди исходящих команд для этого канала связи
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();

            m_mapCalibrationP.put( strIdentifier, new HVV4_HvCalibration(
                                GetAMSRoot() + File.separator +
                                "etc" + File.separator +
                                "calibration" + File.separator +
                                "HVV4.HV." + strIdentifier + ".calib.P.xml", "CalibrationP"));
            
            m_mapCalibrationI.put( strIdentifier, new HVV4_HvCalibration(
                                GetAMSRoot() + File.separator +
                                "etc" + File.separator +
                                "calibration" + File.separator +
                                "HVV4.HV." + strIdentifier + ".calib.I.xml", "CalibrationI"));
            
            m_mapCalibrationU.put( strIdentifier, new HVV4_HvCalibration(
                                GetAMSRoot() + File.separator +
                                "etc" + File.separator +
                                "calibration" + File.separator +
                                "HVV4.HV." + strIdentifier + ".calib.U.xml", "CalibrationU"));
            
            m_mapSerials.put( strIdentifier, serialPort);
            m_mapSerialListeners.put( strIdentifier, evListener);
            m_mapCircleBuffers.put( strIdentifier, cBuffer);
            m_mapProcessorsRunnables.put( strIdentifier, processor);
            m_mapProcessorsThreads.put( strIdentifier, thread);
            m_mapCommands.put( strIdentifier, q);
        }
        catch( SerialPortException ex) {
            logger.error( "При попытке соединения c " + strPort + " получили исключительную ситуацию:\n\n", ex);
        }
    }
    
    public HVV4_HvApp() {
        m_strAMSrootEnvVar = System.getenv( "AMS_ROOT");
        
        //SETTINGS
        m_Settings = new HVV4_HvSettings(m_strAMSrootEnvVar);
        
        //ПРОВЕРКА ОДНОВРЕМЕННОГО ЗАПУСКА ТОЛЬКО ОДНОЙ КОПИИ ПРОГРАММЫ
        try {
            m_pSingleInstanceSocketServer = new ServerSocket( m_Settings.GetSingleInstanceSocketServerPort());
        }
        catch( Exception ex) {
            MessageBoxError( "Модуль управления высоким напряжением уже запущен.\n", "Модуль управления высоким напряжением");
            logger.error( "Не смогли открыть сокет для проверки запуска только одной копии программы! Программа уже запущена?", ex);
            m_pSingleInstanceSocketServer = null;
            m_Resources = null;
            m_mapSerials = null;
            m_mapSerialListeners = null;
            m_mapCircleBuffers = null;
            m_mapU = null;
            m_mapI = null;
            m_mapProcessorsRunnables = null;
            m_mapProcessorsThreads = null;
            m_mapCommands = null;
            m_mapCalibrationP = null;
            m_mapCalibrationI = null;
            m_mapCalibrationU = null;
            return;
        }
        
        //RESOURCES
        m_Resources = HVV_Resources.getInstance();
        
        m_mapSerials = new TreeMap();
        m_mapSerialListeners = new TreeMap();
        m_mapCircleBuffers = new TreeMap();        
        m_mapU = new TreeMap();
        m_mapI = new TreeMap();
        m_mapProcessorsRunnables = new TreeMap();
        m_mapProcessorsThreads = new TreeMap();
        m_mapCommands = new TreeMap();
        m_mapCalibrationP = new TreeMap();
        m_mapCalibrationI = new TreeMap();
        m_mapCalibrationU = new TreeMap();
    }
    
    public void start() {
        
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
        java.util.logging.Logger.getLogger( HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (InstantiationException ex) {
        java.util.logging.Logger.getLogger( HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (IllegalAccessException ex) {
        java.util.logging.Logger.getLogger( HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (javax.swing.UnsupportedLookAndFeelException ex) {
        java.util.logging.Logger.getLogger( HVV4_HvMainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>
        //</editor-fold>



        //MAINFRAME
        m_pMainWnd = new HVV4_HvMainFrame( this);
        
        OpenPort( GetSettings().GetPort1A(), "1A");
        OpenPort( GetSettings().GetPort2A(), "2A");
        OpenPort( GetSettings().GetPort3A(), "3A");
        OpenPort( GetSettings().GetPort4A(), "4A");
        OpenPort( GetSettings().GetPort1T(), "1T");
        OpenPort( GetSettings().GetPort2T(), "2T");
        OpenPort( GetSettings().GetPort3T(), "3T");
        OpenPort( GetSettings().GetPort4T(), "4T");
        
        java.awt.EventQueue.invokeLater( new Runnable() {
            public void run() {
                m_pMainWnd.setVisible( true);
            }
        });
        
    }
    
    /*
    public int GetPcCodeForI( String strId, int nDesiredCurrent) {
        if( m_mapCalibrationP == null)
            return 0;
        
        HVV4_HvCalibration pCalib = ( HVV4_HvCalibration) m_mapCalibrationP.get( strId);
        if( pCalib == null || pCalib.m_pCalibration.size() < 2)
            return 0;
        
        Set set = pCalib.m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nCurr1, nCurr2;
        
        it = set.iterator(); 
        
        Map.Entry entry = (Map.Entry) it.next();
        nCode1 = ( int) entry.getKey();
        nCurr1 = ( int) entry.getValue();
        
        entry = (Map.Entry) it.next();
        nCode2 = ( int) entry.getKey();
        nCurr2 = ( int) entry.getValue();

        if( nDesiredCurrent > nCurr2) {
            while( it.hasNext()) {        
                entry = (Map.Entry) it.next();
                nCode1 = nCode2;
                nCurr1 = nCurr2;
                nCode2 = ( int) entry.getKey();
                nCurr2 = ( int) entry.getValue();
                if( nDesiredCurrent <= nCurr2) break;
            }
        }
        
        double k = ( ( double) ( nCode2 - nCode1)) / ( ( double) ( nCurr2 - nCurr1));
        int nResult = nCode1 + ( int) ( k * ( nDesiredCurrent - nCurr1));
        
        logger.debug( String.format("CODE1=%d\tCODE2=%d", nCode1, nCode2));
        logger.debug( String.format("CURR1=%d\tCURR2=%d", nCurr1, nCurr2));
        logger.debug( String.format("DSRDI=%d\tRESLT=%d", nDesiredCurrent, nResult));
        
        return nResult;
    }
    
    public int GetIForMgCode( String strId, int nMgCode) {
        if( m_mapCalibrationI == null)
            return 0;
        
        HVV4_HvCalibration pCalib = ( HVV4_HvCalibration) m_mapCalibrationI.get( strId);
        if( pCalib == null || pCalib.m_pCalibration.size() < 2)
            return 0;
        
        Set set = pCalib.m_pCalibration.entrySet();
        Iterator it;
        
        int nCode1, nCode2;
        int nCurr1, nCurr2;
        
        it = set.iterator(); 
        
        Map.Entry entry = (Map.Entry) it.next();
        nCode1 = ( int) entry.getKey();
        nCurr1 = ( int) entry.getValue();
                
        entry = (Map.Entry) it.next();
        nCode2 = ( int) entry.getKey();
        nCurr2 = ( int) entry.getValue();
        
        if( nMgCode > nCode2) {
            while( it.hasNext()) {        
                entry = (Map.Entry) it.next();
                nCode1 = nCode2;
                nCurr1 = nCurr2;
                nCode2 = ( int) entry.getKey();
                nCurr2 = ( int) entry.getValue();
                if( nMgCode <= nCode2) break;
            }
        }
        
        double k = ( ( double) ( nCurr2 - nCurr1)) / ( ( double) ( nCode2 - nCode1));
        int nResult = nCurr1 + ( int) ( k * ( nMgCode - nCode1));
        
        logger.debug( String.format("CODE1=%d\tCODE2=%d", nCode1, nCode2));
        logger.debug( String.format("CURR1=%d\tCURR2=%d", nCurr1, nCurr2));
        logger.debug( String.format("CODE =%d\tRESLT=%d", nMgCode, nResult));
        return nResult;
    }
    
    public int GetUForMgCode( String strId, int nMgCode) {
        if( m_mapCalibrationU == null)
            return 0;
        
        HVV4_HvCalibration pCalib = ( HVV4_HvCalibration) m_mapCalibrationU.get( strId);
        if( pCalib == null || pCalib.m_pCalibration.size() < 2)
            return 0;
        
        Set set = pCalib.m_pCalibration.entrySet();
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
        
        if( nMgCode > nCode2) {
            while( it.hasNext()) {        
                entry = (Map.Entry) it.next();
                nCode1 = nCode2;
                nVolt1 = nVolt2;
                nCode2 = ( int) entry.getKey();
                nVolt2 = ( int) entry.getValue();
                if( nMgCode <= nCode2) break;
            }
        }
        
        double k = ( ( double) ( nVolt2 - nVolt1)) / ( ( double) ( nCode2 - nCode1));
        int nResult = nVolt1 + ( int) ( k * ( nMgCode - nCode1));
        
        logger.debug( String.format("CODE1=%d\tCODE2=%d", nCode1, nCode2));
        logger.debug( String.format("VOLT1=%d\tVOLT2=%d", nVolt1, nVolt2));
        logger.debug( String.format("CODE =%d\tRESLT=%d", nMgCode, nResult));
        return nResult;
    }*/
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        //BasicConfigurator.configure();
        //logger.setLevel( Level.DEBUG);
        
        //главная переменная окружения
        String strAMSrootEnvVar = System.getenv( "AMS_ROOT");
        if( strAMSrootEnvVar == null) {
            MessageBoxError( "Не задана переменная окружения AMS_ROOT!", "HVV4.HV");
            return;
        }
        
        //настройка логгера
        String strlog4jPropertiesFile = strAMSrootEnvVar + File.separator +
                                            "etc" + File.separator + 
                                            "log4j" + File.separator +
                                            "log4j.hvv4.hv.properties";
        File file = new File( strlog4jPropertiesFile);
        if(!file.exists())
            System.out.println("It is not possible to load the given log4j properties file :" + file.getAbsolutePath());
        else
            PropertyConfigurator.configure( file.getAbsolutePath());
        
        //запуск программы
        HVV4_HvApp appInstance = new HVV4_HvApp();
        if( appInstance.m_pSingleInstanceSocketServer != null) {
            logger.info( "HVV4_HV::main(): Start point!");
            appInstance.start();
        }
    }
    
    /**
     * Функция для сообщения пользователю информационного сообщения
     * @param strMessage сообщение
     * @param strTitleBar заголовок
     */
    public static void MessageBoxInfo( String strMessage, String strTitleBar)
    {
        JOptionPane.showMessageDialog( null, strMessage, strTitleBar, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Функция для сообщения пользователю сообщения об ошибке
     * @param strMessage сообщение
     * @param strTitleBar заголовок
     */
    public static void MessageBoxError( String strMessage, String strTitleBar)
    {
        JOptionPane.showMessageDialog( null, strMessage, strTitleBar, JOptionPane.ERROR_MESSAGE);
    }
    
    public Date GetLocalDate() {
        Date dt = new Date( System.currentTimeMillis() - 1000 * 60 * 60 * GetSettings().GetTimeZoneShift());
        return dt;
    }
    
    public PortReader createPortReader( String strId) {
        return new PortReader( this, strId);
    }
    
    public class PortReader implements SerialPortEventListener {

        final HVV4_HvApp theApp;
        final String m_strIdentifier;
        
        public PortReader( HVV4_HvApp app, String strIdentifier) {
            theApp = app;
            m_strIdentifier = strIdentifier;
        }
        
        @Override
        public void serialEvent(SerialPortEvent event) {            
            SerialPort port = ( SerialPort) theApp.m_mapSerials.get( m_strIdentifier);
            HVV4_HV_CircleBuffer circleBuffer = ( HVV4_HV_CircleBuffer) theApp.m_mapCircleBuffers.get( m_strIdentifier);
            
            if( port == null) { logger.error( "NULL port"); return; }
            if( circleBuffer == null) { logger.error( "NULL cbuffer"); return; }
            
            if( event.isRXCHAR() && event.getEventValue() > 0){
                try {
                    //Получаем ответ от устройства, обрабатываем данные и т.д.
                    int nReadyBytes = event.getEventValue();
                    byte bts[] = new byte[ nReadyBytes];
                    bts = port.readBytes( nReadyBytes);
                    circleBuffer.AddBytes( bts, nReadyBytes);
                }
                catch (SerialPortException ex) {
                    logger.error( "SerialPortException caught", ex);
                }
            }
        }
    }
}
