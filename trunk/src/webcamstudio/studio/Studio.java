/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.studio;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import webcamstudio.channels.MasterChannels;
import webcamstudio.mixers.MasterMixer;
import webcamstudio.streams.*;

/**
 *
 * @author patrick
 */
public class Studio {

    ArrayList<String> channels = MasterChannels.getInstance().getChannels();
    ArrayList<Stream> streams = MasterChannels.getInstance().getStreams();
    private static final String ELEMENT_SOURCES = "Sources";
    private static final String ELEMENT_CHANNELS = "Channels";
    private static final String ELEMENT_SOURCE = "Source";
    private static final String ELEMENT_CHANNEL = "Channel";
    private static final String ELEMENT_ROOT = "WebcamStudio";
    private static final String ELEMENT_MIXER = "Mixer";

    protected Studio() {
    }

    public ArrayList<String> getChannels() {
        return channels;
    }

    public ArrayList<Stream> getStreams() {
        return streams;
    }

    public static Studio load(File file) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        Studio studio = new Studio();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        XPath path = XPathFactory.newInstance().newXPath();

        //Loading channels
        NodeList nodeChannels = (NodeList) path.evaluate("/WebcamStudio/Channels/Channel", doc.getDocumentElement(), XPathConstants.NODESET);
        for (int i = 0; i < nodeChannels.getLength(); i++) {
            Node channel = nodeChannels.item(i);
            String name = channel.getAttributes().getNamedItem("name").getTextContent();
            System.out.println("Channel " + name);
            studio.channels.add(name);
        }

        // Loading mixer settings
        Node nodeMixer = (Node) path.evaluate("/WebcamStudio/Mixer", doc.getDocumentElement(), XPathConstants.NODE);
        String width = nodeMixer.getAttributes().getNamedItem("width").getTextContent();
        String height = nodeMixer.getAttributes().getNamedItem("height").getTextContent();
        String rate = nodeMixer.getAttributes().getNamedItem("rate").getTextContent();
        System.out.println("Mixer: " + width + "X" + height + "@" + rate + "fps");

        return studio;
    }

    public static void save(File file) throws IOException, XMLStreamException, TransformerConfigurationException, TransformerException, IllegalArgumentException, IllegalAccessException {
        ArrayList<String> channels = MasterChannels.getInstance().getChannels();
        ArrayList<Stream> streams = MasterChannels.getInstance().getStreams();
        StringWriter writer = new StringWriter();

        XMLStreamWriter xml = javax.xml.stream.XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
        xml.writeStartDocument();
        xml.writeStartElement(ELEMENT_ROOT);
        //Channels
        xml.writeStartElement(ELEMENT_CHANNELS);
        for (String c : channels) {
            xml.writeStartElement(ELEMENT_CHANNEL);
            xml.writeAttribute("name", c);
            xml.writeEndElement();
        }
        xml.writeEndElement();

        xml.writeStartElement(ELEMENT_SOURCES);
        for (Stream s : streams) {
            xml.writeStartElement(ELEMENT_SOURCE);
            writeObject(s, xml);
            xml.writeEndElement(); // source
        }
        xml.writeEndElement();  //Sources

        xml.writeStartElement(ELEMENT_MIXER);
        xml.writeAttribute("width", MasterMixer.getInstance().getWidth() + "");
        xml.writeAttribute("height", MasterMixer.getInstance().getHeight() + "");
        xml.writeAttribute("rate", MasterMixer.getInstance().getRate() + "");
        xml.writeEndElement(); //Mixer

        xml.writeEndElement(); //WebcamStudio
        xml.writeEndDocument();
        xml.flush();
        xml.close();
        TransformerFactory factory = TransformerFactory.newInstance();

        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter formattedStringWriter = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(writer.getBuffer().toString())), new StreamResult(file));


    }

    private static void writeObject(Object o, XMLStreamWriter xml) throws IllegalArgumentException, IllegalAccessException, XMLStreamException {

        Field[] fields = o.getClass().getDeclaredFields();
        Field[] superFields = null;
        if (o instanceof Stream) {
            superFields = o.getClass().getSuperclass().getDeclaredFields();
        }
        String clazz = o.getClass().getCanonicalName();
        if (clazz != null) {
            xml.writeAttribute("clazz", clazz);
        }
        if (superFields != null) {
            for (Field f : superFields) {
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(o);
                if (value instanceof Integer) {
                    xml.writeAttribute(name, f.getInt(o) + "");
                } else if (value instanceof Float) {
                    xml.writeAttribute(name, f.getFloat(o) + "");
                }

            }
        }
        for (Field f : fields) {
            f.setAccessible(true);
            String name = f.getName();
            Object value = f.get(o);
            if (value instanceof Integer) {
                xml.writeAttribute(name, f.getInt(o) + "");
            } else if (value instanceof Float) {
                xml.writeAttribute(name, f.getFloat(o) + "");
            }
        }
        if (superFields != null) {
            for (Field f : superFields) {
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(o);
                if (value instanceof String) {
                    xml.writeStartElement(name);
                    xml.writeCData(value.toString());
                    xml.writeEndElement();
                } else if (value instanceof File) {
                    xml.writeStartElement(name);
                    xml.writeCData(((File) value).getAbsolutePath());
                    xml.writeEndElement();
                }
            }
        }

        for (Field f : fields) {
            f.setAccessible(true);
            String name = f.getName();
            Object value = f.get(o);
            if (value instanceof String) {
                xml.writeStartElement(name);
                xml.writeCData(value.toString());
                xml.writeEndElement();
            } else if (value instanceof File) {
                xml.writeStartElement(name);
                xml.writeCData(((File) value).getAbsolutePath());
                xml.writeEndElement();
            }
        }
        if (superFields != null) {
            for (Field f : superFields) {
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(o);
                if (value instanceof List) {
                    xml.writeStartElement(name);
                    for (Object subO : ((List) value)) {
                        writeObject(subO, xml);
                    }
                    xml.writeEndElement();
                }
            }
        }

        for (Field f : fields) {
            f.setAccessible(true);
            String name = f.getName();
            Object value = f.get(o);
            if (value instanceof List) {
                xml.writeStartElement(name);
                for (Object subO : ((List) value)) {
                    writeObject(subO, xml);
                }
                xml.writeEndElement();
            }
        }
    }

    private static void readStreams(Document xml) throws IllegalArgumentException, IllegalAccessException, XPathExpressionException {
        XPath path = XPathFactory.newInstance().newXPath();
        NodeList sources = (NodeList) path.evaluate("/" + ELEMENT_ROOT + "/" + ELEMENT_SOURCES + "/" + ELEMENT_SOURCE, xml.getDocumentElement(), XPathConstants.NODESET);
        if (sources != null) {
            for (int i = 0; i < sources.getLength(); i++) {
                Node source = sources.item(i);
                String clazz = source.getAttributes().getNamedItem("clazz").getTextContent();
                String file = null;
                Stream stream = null;
                for (int j = 0; j < source.getChildNodes().getLength(); j++) {
                    Node child = source.getChildNodes().item(j);
                    if (child.getNodeName().equals("file")) {
                        file = child.getTextContent();
                    }
                }
                if (file != null) {
                    stream = Stream.getInstance(new File(file));
                } else if (clazz.toLowerCase().endsWith("sourcedesktop")) {
                    stream = new SourceDesktop();
                } else if (clazz.toLowerCase().endsWith("sourcetext")) {
                    stream = new SourceText("");
                } else if (clazz.toLowerCase().endsWith("sourceqrcode")) {
                    stream = new SourceQRCode("");
                } else if (clazz.toLowerCase().endsWith("sourcemicrophone")) {
                    stream = new SourceMicrophone();
                } else {
                    System.err.println("Cannot handle " + clazz);
                }
            }
        }

    }

    private static void readObject(Stream stream, Node source) throws IllegalArgumentException, IllegalAccessException {
        XPath path = XPathFactory.newInstance().newXPath();

        Field[] fields = stream.getClass().getDeclaredFields();
        Field[] superFields = superFields = stream.getClass().getSuperclass().getDeclaredFields();
        // Read integer and floats
        for (Field field : superFields) {
            field.setAccessible(true);
            String name = field.getName();
            String value = null;
            if (source.getAttributes().getNamedItem(name) != null) {
                value = source.getAttributes().getNamedItem(name).getTextContent();
                if (field.get(stream) instanceof Integer) {
                    field.setInt(stream, new Integer(value));
                } else if (field.get(stream) instanceof Integer) {
                    field.setFloat(stream, new Float(value));
                } else if (field.get(stream) instanceof String) {
                    for (int i = 0; i < source.getChildNodes().getLength(); i++) {
                        Node node = source.getChildNodes().item(i);
                        if (node.getNodeName().equals(name)) {
                            field.set(stream, node.getTextContent());
                        }
                    }
                }

            }
        }


        for (Field field : fields) {
            field.setAccessible(true);
            String name = field.getName();
            String value = null;
            if (source.getAttributes().getNamedItem(name) != null) {
                value = source.getAttributes().getNamedItem(name).getTextContent();
                if (field.get(stream) instanceof Integer) {
                    field.setInt(stream, new Integer(value));
                } else if (field.get(stream) instanceof Integer) {
                    field.setFloat(stream, new Float(value));
                } else if (field.get(stream) instanceof String) {
                    for (int i = 0; i < source.getChildNodes().getLength(); i++) {
                        Node node = source.getChildNodes().item(i);
                        if (node.getNodeName().equals(name)) {
                            field.set(stream, node.getTextContent());
                        }
                    }
                }

            }
        }
        // Read List
        
    }

    public static void main(String[] args) {
        try {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("/home/patrick/Desktop/test.studio"));
                readStreams(doc);

            } catch (IllegalArgumentException ex) {
                Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XPathExpressionException ex) {
                Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(Studio.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
