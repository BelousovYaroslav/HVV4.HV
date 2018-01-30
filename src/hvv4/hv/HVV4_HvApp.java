/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv;

import java.io.File;
import java.net.ServerSocket;
import java.util.Date;
import javax.swing.JOptionPane;
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
    
    //private final HVV_Resources m_Resources;
    //public HVV_Resources GetResources() { return m_Resources;}
    
    private final HVV4_HvSettings m_Settings;
    public HVV4_HvSettings GetSettings() { return m_Settings;}
    
    private ServerSocket m_pSingleInstanceSocketServer;
    
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
            //m_Resources = null;
            return;
        }
        
        //RESOURCES
        //m_Resources = HVV_Resources.getInstance();
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
            MessageBoxError( "Не задана переменная окружения AMS_ROOT!", "HVV_Poller");
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
            logger.info( "HVV_Poller::main(): Start point!");
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
}
