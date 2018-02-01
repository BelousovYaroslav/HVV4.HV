/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

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
                try {
                    thread.join( 1000);
                    bStopped = false;
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
        
        serialPort = new SerialPort( strPort);
        try {
            //Открываем порт
            serialPort.openPort();

            //Выставляем параметры
            serialPort.setParams( 38400,
                                 SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            //Включаем аппаратное управление потоком
            //serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | 
            //                              SerialPort.FLOWCONTROL_RTSCTS_OUT);

            //Устанавливаем ивент лисенер и маску
            evListener = new PortReader( this, strIdentifier);
            serialPort.addEventListener( evListener, SerialPort.MASK_RXCHAR);
            
            cBuffer = new HVV4_HV_CircleBuffer();
            HVV4_HV_StreamProcessingThread processor = new HVV4_HV_StreamProcessingThread( this, strIdentifier);
            Thread thread = new Thread( processor);
            thread.start();
                    
            m_mapSerials.put( strIdentifier, serialPort);
            m_mapSerialListeners.put( strIdentifier, evListener);
            m_mapCircleBuffers.put( strIdentifier, cBuffer);
            m_mapProcessorsRunnables.put( strIdentifier, processor);
            m_mapProcessorsThreads.put( strIdentifier, thread);
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
        
        OpenPort( "/dev/ttyUSB0", "1A");
        OpenPort( "/dev/ttyUSB1", "2A");
        OpenPort( "/dev/ttyUSB2", "3A");
        OpenPort( "/dev/ttyUSB3", "4A");
        OpenPort( "/dev/ttyUSB4", "1T");
        OpenPort( "/dev/ttyUSB5", "2T");
        OpenPort( "/dev/ttyUSB6", "3T");
        OpenPort( "/dev/ttyUSB7", "4T");
        
        java.awt.EventQueue.invokeLater( new Runnable() {
            public void run() {
                m_pMainWnd.setVisible( true);
            }
        });
        
    }
    
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
        String strlog4jPropertiesFile = strAMSrootEnvVar + "/etc/log4j.hvv4hv.properties";
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
    
    private class PortReader implements SerialPortEventListener {

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
