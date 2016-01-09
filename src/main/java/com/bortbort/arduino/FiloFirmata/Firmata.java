package com.bortbort.arduino.FiloFirmata;

import com.bortbort.arduino.FiloFirmata.Listeners.MessageListener;
import com.bortbort.arduino.FiloFirmata.Messages.Message;
import com.bortbort.arduino.FiloFirmata.Messages.TransmittableMessage;
import com.bortbort.arduino.FiloFirmata.Parser.CommandParser;
import com.bortbort.arduino.FiloFirmata.Parser.MessageBuilder;
import com.bortbort.arduino.FiloFirmata.Parser.SysexCommandParser;
import com.bortbort.arduino.FiloFirmata.Parser.SysexMessageBuilder;
import com.bortbort.arduino.FiloFirmata.PortAdapters.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Implements the Firmata protocol with a Firmata supported device, using any number of custom commands. This library
 * supports adapting any serial port library with it. Within the configuration you can dictate a custom SerialPort
 * implementation just as JSSC, RXTX, etc (provided you write them). The default implementation uses PureJavaSerialComm.
 */
public class Firmata extends SerialPortEventListener {
    private static final Logger log = LoggerFactory.getLogger(Firmata.class);

    /**
     * Map of listener objects registered to respond to specific message events
     */
    private final HashMap<Class, ArrayList<MessageListener>> messageListenerMap = new HashMap<>();

    /**
     * Serial port adapter reference
     */
    private SerialPort serialPort;

    /**
     * Firmata configuration reference
     */
    private FirmataConfiguration configuration;

    /**
     * Flag identifying if the Firmata library (and serial port) is started
     */
    private Boolean started = false;


    /**
     * Add a custom parser to the Firmata library. When the command byte for the parser is received, the parser
     * will be responsible for turning the the data that follows into a Firmata message.
     *
     * @param messageBuilder Builder class that translates the byte message into a message object
     */
    public static void addCustomCommandParser(MessageBuilder messageBuilder) {
        CommandParser.addParser(messageBuilder);
    }

    /**
     * Add a custom sysex parser to the Firmata library. When the command byte for the parser is received, the parser
     * will be responsible for turning the the data that follows into a Firmata message.
     *
     * @param messageBuilder SysexMessageBuilder object describing how to build a message
     */
    public static void addCustomSysexParser(SysexMessageBuilder messageBuilder) {
        SysexCommandParser.addParser(messageBuilder);
    }


    /**
     * Implement the Firmata library using the default FirmataConfiguration()
     */
    public Firmata() {
        this.configuration = new FirmataConfiguration();
    }

    /**
     * Implement the Firmata library using a custom FirmataConfiguration()
     *
     * @param configuration FirmataConfiguration custom object to match your port/api needs
     */
    public Firmata(FirmataConfiguration configuration) {
        this.configuration = new FirmataConfiguration(configuration);
    }

    /**
     * Add a messageListener to the Firmta object which will fire whenever a matching message is received
     * over the SerialPort.
     *
     * @param messageListener MessageListener object to handle a received Message event over the SerialPort.
     * */
    public void addMessageListener(MessageListener messageListener) {
        Class messageListenerClass = messageListener.getMessageType();

        if (!messageListenerMap.containsKey(messageListenerClass)) {
            ArrayList<MessageListener> listenerArray = new ArrayList<>();
            listenerArray.add(messageListener);
            messageListenerMap.put(messageListenerClass, listenerArray);
        }
        else {
            messageListenerMap.get(messageListenerClass).add(messageListener);
        }
    }


    /**
     * Remove a messageListener from the Firmata object which will stop the listener from responding to message
     * received events over the SerialPort.
     *
     * @param messageListener MessageListener object to handle a received Message event over the SerialPort.
     */
    public void removeMessageListener(MessageListener messageListener) {
        Class messageListenerClass = messageListener.getMessageType();
        if (messageListenerMap.containsKey(messageListenerClass)) {
            messageListenerMap.get(messageListenerClass).remove(messageListener);
        }
    }

    /**
     * Send a message over the serial port to a Firmata supported device.
     *
     * @param message TransmittableMessage object used to translate a series of bytes to the SerialPort.
     * @return True if message sent. False if message was not sent.
     */
    public Boolean sendMessage(TransmittableMessage message) {
        try {
            serialPort.getOutputStream().write(message.serialize());
            return true;
        } catch (IOException e) {
            log.error("Unable to transmit message {} through serial port", message.getClass().getName());
            stop();
        }

        return false;
    }

    /**
     * Starts the firmata library. If the library is already started this will just return true.
     * Starts the SerialPort and handlers, and ensures the attached device is able to communicate with our
     * library. This will return an UnsupportedDevice exception if the attached device does not meet the library
     * requirements.
     *
     * @return True if the library started. False if the library failed to start.
     */
    public synchronized Boolean start() {
        if (started) {
            return true;
        }

        createSerialPort();

        serialPort.addEventListener(this);

        if (!serialPort.connect()) {
            log.error("Failed to start Firmata Library. Cannot connect to Serial Port.");
            log.error("Configuration is {}", configuration);
            stop();
            return false;
        }

        started = true;
        return true;
    }

    /**
     * Stops the Firmata library. If the library is already stopped, this will still attempt to stop anything
     * that may still be lingering. Take note that many calls could eventually result in noticeable cpu usage.
     *
     * @return True if the library has stopped. False if there was an error stopping the library.
     */
    public synchronized  Boolean stop() {
        if (serialPort != null) {
            serialPort.removeEventListener(this);
        }

        if (!removeSerialPort()) {
            log.error("Failed to stop Firmata Library. Cannot close Serial Port.");
            log.error("Configuration is {}", configuration);
            return false;
        }

        started = false;
        return true;
    }


    /**
     * Generate the SerialPort object using the SerialPort adapter class provided in the FirmataConfiguration
     * If there is any issue constructing this object, toss a RuntimeException, as this is a developer error in
     * implementing the SerialPort adapter for this library.
     */
    private void createSerialPort() {
        Constructor<? extends SerialPort> constructor;

        try {
            constructor = configuration.getSerialPortAdapterClass().getDeclaredConstructor(
                    String.class, Integer.class,
                    SerialPortDataBits.class, SerialPortStopBits.class, SerialPortParity.class);
        } catch (NoSuchMethodException e) {
            log.error("Unable to construct SerialPort object. Programming error. Your class adapter must support " +
                    "a constructor with input args of" +
                    "YourSerialPort(String.class, Integer.class, SerialPortDataBits.class, " +
                    "SerialPortStopBits.class, SerialPortParity.class);");
            e.printStackTrace();
            throw new RuntimeException("Cannot construct SerialPort adapter!");
        }

        try {
            serialPort = constructor.newInstance(
                    configuration.getSerialPortID(),
                    configuration.getSerialPortBaudRate(),
                    configuration.getSerialPortDataBits(),
                    configuration.getSerialPortStopBits(),
                    configuration.getSerialPortParity());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("Unable to construct SerialPort object. Programming error. Instantiation error. {}",
                    e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Cannot construct SerialPort adapter!");
        }
    }

    /**
     * Disconnect from the SerialPort object that we are communicating with over the Firmata protocol.
     * @return True if the SerialPort was closed. False if the port failed to close.
     */
    private Boolean removeSerialPort() {
        Boolean ret = true;

        if (serialPort != null) {
            ret = serialPort.disconnect();
            serialPort = null;
        }

        return ret;
    }


    /**
     * Handle events from the SerialPort object. When DATA_AVAILABLE is sent, handleDataAvailable() and build a message
     * object that can be passed up to the client for interpretation and handling.
     * Note: Currently only handling DATA_AVAILABLE
     * @param event SerialPortEvent indicating the type of event that was raised from the SerialPort object
     */
    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() == SerialPortEventTypes.DATA_AVAILABLE) {
            handleDataAvailable();
        }
    }

    /**
     * Handles the SerialPort input stream and builds a message object if a detected CommandByte is discovered
     * over the communications stream
     */
    private void handleDataAvailable() {
        InputStream inputStream = serialPort.getInputStream();
        try {
            while (inputStream.available() > 0) {
                byte inputByte = (byte) inputStream.read();
                Message message = CommandParser.handleByte(inputByte, inputStream);
                if (message != null) {
                    log.info("Dispatching message {}", message.getClass().getName());
                    dispatchMessage(message);
                }
            }
        } catch (IOException e) {
            log.error("IO Error reading from serial port. Closing connection.");
            e.printStackTrace();
            stop();
        }
    }

    /**
     * Dispatch a Message object built from data over the SerialPort communication line to a corresponding
     * MessageListener class designed to handle and interpret the object for processing in the client code.
     * It seems unchecked warning suppression is is necessary, since this is the only part of the
     * design where we need to translate from generics to implementations, and the only objects
     * that know what Message implementation this is are the MessageListener implementations, which
     * we are also generic here.
     */
    @SuppressWarnings("unchecked")
    public void dispatchMessage(Message message) {
        Class messageClass = message.getClass();
        if (messageListenerMap.containsKey(messageClass)) {
            ArrayList<MessageListener> messageListeners = messageListenerMap.get(messageClass);
            for (MessageListener listener : messageListeners) {
                listener.messageReceived(message);
            }
        }
    }
}