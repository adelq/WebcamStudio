/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * StreamDesktop.java
 *
 * Created on 15-Apr-2012, 12:29:14 AM
 */
package webcamstudio.components;

import java.awt.BorderLayout;
import webcamstudio.streams.SourceText;
import webcamstudio.streams.SourceWebcam;
import webcamstudio.streams.Stream;

/**
 *
 * @author patrick
 */
public class StreamDesktop extends javax.swing.JInternalFrame {

    StreamPanel panel = null;
    Stream stream = null;
    Listener listener = null;
    public interface Listener{
        public void selectedSource(Stream source);
    }
    /** Creates new form StreamDesktop */
    public StreamDesktop(Stream s,Listener l) {
        listener = l;
        stream = s;
        initComponents();
        if (s instanceof SourceText) {
            StreamPanelText p = new StreamPanelText((SourceText)s);
            this.setLayout(new BorderLayout());
            this.add(p, BorderLayout.CENTER);
            this.setTitle(s.getName());
            this.setVisible(true);
        } else {
            StreamPanel p = new StreamPanel(s);
            this.setLayout(new BorderLayout());
            this.add(p, BorderLayout.CENTER);
            this.setTitle(s.getName());
            this.setVisible(true);
            panel = p;
        }
        this.setVisible(true);
        this.setDesktopIcon(new DesktopIcon(this,s));
        this.setClosable(!(stream instanceof SourceWebcam));
        this.setToolTipText(stream.getName());
        pack();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setClosable(true);
        setIconifiable(true);
        setMaximizable(true);
        setFrameIcon(new javax.swing.ImageIcon(getClass().getResource("/webcamstudio/resources/tango/user-desktop.png"))); // NOI18N
        addInternalFrameListener(new javax.swing.event.InternalFrameListener() {
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent evt) {
                formInternalFrameClosing(evt);
            }
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameIconified(javax.swing.event.InternalFrameEvent evt) {
                formInternalFrameIconified(evt);
            }
            public void internalFrameDeiconified(javax.swing.event.InternalFrameEvent evt) {
                formInternalFrameDeiconified(evt);
            }
            public void internalFrameActivated(javax.swing.event.InternalFrameEvent evt) {
                formInternalFrameActivated(evt);
            }
            public void internalFrameDeactivated(javax.swing.event.InternalFrameEvent evt) {
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                formFocusGained(evt);
            }
        });

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formInternalFrameIconified(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_formInternalFrameIconified
       if (panel!=null){
        this.setFrameIcon(panel.getIcon());
       }
    }//GEN-LAST:event_formInternalFrameIconified

    private void formInternalFrameDeiconified(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_formInternalFrameDeiconified
    }//GEN-LAST:event_formInternalFrameDeiconified

    private void formInternalFrameClosing(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_formInternalFrameClosing
        stream.destroy();
        stream = null;
        panel = null;

    }//GEN-LAST:event_formInternalFrameClosing

    private void formFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_formFocusGained
        
    }//GEN-LAST:event_formFocusGained

    private void formInternalFrameActivated(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_formInternalFrameActivated
        
        if (listener!=null){
            new Thread(new Runnable(){

                @Override
                public void run() {
                    listener.selectedSource(stream);
                }
            }).start();
            
        }
    }//GEN-LAST:event_formInternalFrameActivated

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
