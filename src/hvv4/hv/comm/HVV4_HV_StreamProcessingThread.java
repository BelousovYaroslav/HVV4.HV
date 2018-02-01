/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hvv4.hv.comm;

import hvv4.hv.HVV4_HvApp;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventListener;
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
        
        do {
            HVV4_HV_CircleBuffer cBuffer = ( HVV4_HV_CircleBuffer) theApp.m_mapCircleBuffers.get( m_strIdentifier);
            int nLen = cBuffer.getReadyIncomingDataLen();
            if( nLen > 0) {
                if( m_bWaitingResponse) {
                    //мы ждём ответа
                    
                    if( nLen < 5) {
                        //мы принимаем только 5-байтные ответы... меньше - может не долетело? подождем
                        continue;
                    }
                    else {
                        if( nLen == 5) {
                            //это наш 5-х байтный ответ... ок

                            byte [] bts = new byte[ 5];
                            cBuffer.getAnswer( 5, bts);

                            if( bts[4] == 0x0D) {
                                //ответ корректный
                                int nB0 = bts[0] & 0xFF;
                                int nB1 = bts[1] & 0xFF;
                                int nB2 = bts[2] & 0xFF;
                                int nB3 = bts[3] & 0xFF;
                                int nUval = ( nB1 << 8) + nB0;
                                int nIval = ( nB3 << 8) + nB2;
                                theApp.m_mapU.put( m_strIdentifier, nUval);
                                theApp.m_mapI.put( m_strIdentifier, nIval);
                            }
                            else {
                                //а ответ пришёл корявый!
                                logger.warn( m_strIdentifier + " FAILED!");
                                //TODO FAIL
                            }
                        
                        }
                        else {
                            //пришло больше 5 байт. Охренеть!
                            byte [] bts = new byte[ nLen];
                            cBuffer.getAnswer( nLen, bts);
                            logger.warn( m_strIdentifier + " FAILED!");
                            //TODO FAIL
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
                    
                    //в любом случае забираем из CircleBuffer
                    byte [] bts = new byte[ nLen];
                    cBuffer.getAnswer( nLen, bts);
                        
                    if( nLen == 1) {
                        if( bts[0] == 0xFE) {
                            logger.warn( "Отловлен одинокий странный FE");
                        }
                        else {
                            logger.warn( m_strIdentifier + " FAILED!");
                            //TODO FAIL
                        }
                    }
                    else {
                        logger.warn( m_strIdentifier + " FAILED!");
                        //TODO FAIL
                    }
                }
            }
            
        } while( m_bStopThread == false);
    }
}
