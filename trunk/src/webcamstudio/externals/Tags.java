/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.externals;

/**
 *
 * @author patrick
 */
public enum Tags {
   
        OWIDTH("@OWIDTH"),
        OHEIGHT("@OHEIGHT"),
        CWIDTH("@CWIDTH"),
        CHEIGHT("@CHEIGHT"),
        RATE("@RATE"),
        SEEK("@SEEK"),
        VPORT("@VPORT"),
        APORT("@APORT"),
        FILE("@FILE"),
        FREQ("@FREQ"),
        BITSIZE("@BITSIZE"),
        CHANNELS("@CHANNELS"),
        VCODEC("@VCODEC"),
        ACODEC("@ACODEC"),
        VBITRATE("@VBITRATE"),
        ABITRATE("@ABITRATE"),
        DESKTOPX("@DESKTOPX"),
        DESKTOPY("@DESKTOPY"),
        DESKTOPW("@DESKTOPW"),
        DESKTOPH("@DESKTOPH"),
        DESKTOPENDX("@DESKTOPENDX"),
        DESKTOPENDY("@DESKTOPENDY"),
        URL("@URL");
        
    private String name = "";
    private Tags(String name) {
        this.name = name;
    }
    public String toString() {
    return name;
    }
}    

