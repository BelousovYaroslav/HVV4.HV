/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv.comm;

import hvv4.hv.HVV4_HvApp;
import hvv4.hv.calibration.HVV4_HvCalibration;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 *
 * @author yaroslav
 */
public class HVV4_HV_StreamProcessingThread implements Runnable {
    private final HVV4_HvApp theApp;
    final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(HVV4_HV_StreamProcessingThread.class);

    public boolean m_bRunning;
    public boolean m_bStopThread;
    
    private final String m_strIdentifier;
    public String GetIdentifier() { return m_strIdentifier;}
    
    private Timer m_TimeOut;
    private boolean m_bWaitingResponse;
    public boolean GetWaitingForResponse() { return m_bWaitingResponse; }
    
    public boolean WaitForRespond() {
        boolean bResult = false;
        
        if( m_TimeOut == null) {
            m_bWaitingResponse = true;
            m_TimeOut = new Timer( 100, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    logger.warn( m_strIdentifier + " TIMEOUT");
                    m_TimeOut.stop();
                    //theApp.m_mapStates.put( m_strIdentifier, HVV4_HvApp.STATE_TIMEOUT);
                    m_bWaitingResponse = false;
                    m_TimeOut = null;
                }
                
            });
            logger.trace( m_strIdentifier + " TIMEOUT STARTED!");
            m_TimeOut.start();
            bResult = true;
        }

        return bResult;
    }
    
    public HVV4_HV_StreamProcessingThread( HVV4_HvApp app, String strIdentifier) {
        theApp = app;
        m_strIdentifier = strIdentifier;
        m_bRunning = false;
        m_bStopThread = false;
        m_TimeOut = null;
    }
    
    @Override
    public void run() {
        m_bRunning = true;
        m_bStopThread = false;
        logger.trace( m_strIdentifier + "P0");
        
        do {
            HVV4_HV_CircleBuffer cBuffer = ( HVV4_HV_CircleBuffer) theApp.m_mapCircleBuffers.get( m_strIdentifier);
            if( cBuffer == null) {
                logger.warn( m_strIdentifier + " CBUFFER = NULL");
                continue;
            }
            int nLen = cBuffer.getReadyIncomingDataLen();
            logger.trace( m_strIdentifier + " P1 " + nLen);
            if( nLen > 0) {
                if( m_bWaitingResponse) {
                    //мы ждём ответа
                    //logger.info( "I2 expecting");
                    if( nLen < 5) {
                        //мы принимаем только 5-байтные ответы... меньше - может не долетело? подождем
                        continue;
                    }
                    else {
                        if( nLen == 5) {
                            //это наш 5-х байтный ответ... ок

                            byte [] bts = new byte[ 5];
                            cBuffer.getAnswer( 5, bts);
                            logger.info( String.format( m_strIdentifier + " <<< 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X",
                                    bts[0], bts[1], bts[2], bts[3], bts[4])); 
                            if( bts[4] == 0x0D) {
                                //ответ корректный
                                int nB0 = bts[0] & 0xFF;
                                int nB1 = bts[1] & 0xFF;
                                int nB2 = bts[2] & 0xFF;
                                int nB3 = bts[3] & 0xFF;
                                int nUval = ( nB2 << 8) + nB3;
                                int nIval = ( nB0 << 8) + nB1;
                                
                                if( theApp.GetSettings().GetUseCalibFromMg()) {
                                    if( theApp.m_mapCalibrationI != null) {
                                        HVV4_HvCalibration calib = ( HVV4_HvCalibration) theApp.m_mapCalibrationI.get( m_strIdentifier);
                                        theApp.m_mapI.put( m_strIdentifier, calib.GetValForCode( nIval));
                                    }
                                    if( theApp.m_mapCalibrationU != null) {
                                        HVV4_HvCalibration calib = ( HVV4_HvCalibration) theApp.m_mapCalibrationU.get( m_strIdentifier);
                                        theApp.m_mapU.put( m_strIdentifier, calib.GetValForCode( nUval));
                                    }                                    
                                }
                                else {
                                    theApp.m_mapU.put( m_strIdentifier, nUval);
                                    theApp.m_mapI.put( m_strIdentifier, nIval);
                                }
                            }
                            else {
                                //а ответ пришёл корявый!
                                logger.warn( m_strIdentifier + ": got 5 bytes (they were 5), but there is no 0x0D at the end. Skipping!");
                            }
                        
                        }
                        else {
                            //пришло больше 5 байт. Охренеть!
                            byte [] bts = new byte[ nLen];
                            cBuffer.getAnswer( nLen, bts);
                            
                            String strMsg = m_strIdentifier + " <<< ";
                            for( int i=0; i<nLen; strMsg += String.format( "0x%02X ", bts[i++]));
                            logger.warn( strMsg);
                            logger.warn( m_strIdentifier + ": got more than 5 bytes in respond! Purge them!");
                        }
                    
                        //считаем что ответ пришёл - сбрасываем ожидание
                        if( m_TimeOut != null) {
                            m_TimeOut.stop();
                            m_TimeOut = null;
                        }
                        m_bWaitingResponse = false;
                    }
                }
                else {
                    //мы НЕ ждём ответа.... прилетел FE?
                    //logger.info( "I2 not expecting");
                    
                    //в любом случае забираем из CircleBuffer
                    byte [] bts = new byte[ nLen];
                    cBuffer.getAnswer( nLen, bts);
                    
                    String strMsg = m_strIdentifier + "<<< ";
                    for( int i=0; i<nLen; strMsg += String.format( "0x%02X ", bts[i++]));
                    logger.warn( strMsg);
                            
                    if( nLen == 1) {
                        if( bts[0] == 0xFE) {
                            logger.warn( m_strIdentifier + ": Мы не ждём ответа, однако отловлен одинокий странный 0xFE");
                        }
                        else {
                            logger.warn( m_strIdentifier + ": Мы не ждём ответа, однако имеем один пришедший байт, не 0xFE!");
                        }
                    }
                    else {
                        logger.warn( m_strIdentifier + ": Мы не ждём ответа, однако имеем странные несколько пришедших байт!");
                    }
                }
            }
            try {
                Thread.sleep( 10);
            }
            catch( InterruptedException e) {
                logger.error( e);
                m_bStopThread = true;
                m_bRunning = false;
                return;
            }
            logger.trace( m_strIdentifier + " Q1 " + m_bStopThread);
        } while( m_bStopThread == false);
        
        if( m_TimeOut != null && m_TimeOut.isRunning()) {
            m_TimeOut.stop();
            m_TimeOut = null;
        }
        
        logger.info( m_strIdentifier + " Q2 processor stopped!");
        m_bRunning = false;
    }
}
