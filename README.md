# FiloFirmata Library
Filo Firmata is a Java library designed to interact with the Firmata protocol. At heart Firmata is a duplication of the standard MIDI protocol that has been adapted to work with hardware project boards such as Arduino for controlling and reading values from sensors or custom code embedded within the project board.

The Firmata project is an attempt to standardize and expand the capibilities of an Arduino or similar device by allowing you to do deeper processing from another device, operating system, and programming language, such as a Raspberry Pi or a laptop. The library has some standard support for reading pins, writing to pins, reading analog sensors, etc, but also allows for you to integrate custom commands so that you can implement the library to suit your communication needs. FiloFirmata is a client for this idea, supporting the base command structure within Firmata, with a design pattern that allows you to quickly define custom commands and data that can communicate with your Firmata library implementation within your project board.

FiloFirmata is an event driven library. Every message passed up or down the Arduino device counts as an event.

For more information on the Firmata Protocol and its use on an Ardiuno project board, see https://github.com/firmata/protocol and https://github.com/firmata/arduino


## Integrating With Your Project
The library requires a configuration class. The configuration class provides the ability to customize the data port, bit rates, protocol verification, and more. A default configuration can be generated by simply creating a new object instance of FiloFirmataConfiguration.

For basic usage you will need to only supply a port identifier (COM3, /dev/..). If this is true for you, then you do not need to provide a configuration.

Please note that FiloFirmata creates a copy of the configuration fed to instantiate the library. Any changes to your configuration object after creating the library will be ignored by the library instance.
```java
// No configuration needed for COM3 MS Windows clients
Firmata firmata = new Firmata();
```
```java
// Configuration for an OSX arduino device.
Firmata firmata = new Firmata("/dev/tty.usbmodemXXXXXX");
```
```java
// Custom configuration for an OSX arduino device that needs to communicate slower than default.
FirmataConfiguration firmataConfiguration = new FirmataConfiguration("/dev/tty.usbmodemXXXXXX");
// We want to communicate slower than the StandardFirmata default. Set that here.
firmataConfiguration.setSerialPortBaudRate(9600);
// Create a new firmata instance using the custom configuration. (A copy of the configuration will be generated and used by the library)
Firmata firmata = new Firmata(firmataConfiguration);
// This change ignored by the implemented Firmata library above.
firmataConfiguration.setSerialPortBaudRate(57600);
```


## Firmata Messages
Firmata messages are a simple object that contains values, indexes, and other sets of data that was passed either to or from your project board. Messages that get sent to the project board get serialized into a stream of bytes and are then sent to the project board over a serial communications port. Messages that come from the project board are parsed as a byte stream from the serial port, and built into a message object representing the data that the message contained as a series of Java values or objects for reading and handling within your Java application.
```java
// Example listener that reads protocol version values from a Firmata Message that was sent by the project board.
private final ProtocolVersionListener versionListener = new ProtocolVersionListener() {
    @Override
    public void messageReceived(ProtocolVersionMessage message) {
        // Log the major and minor firmata firmware version reported to us by the Arduino / project board.
        log.info("Detected Firmata device protocol version: {}.{}",
              message.getMajorVersion(), message.getMinorVersion());
    }
};
```


## Basic Usage
```java
// Somewhere in your application you generate a new firmata object (Can be restarted as needed)
Firmata firmata = new Firmata();

// Somewhere in your application you start up the library and begin communications through the serial port.
firmata.start();

// You want to print out to console the firmware name and version of your project board whenever it is sent up
//   So you create a listener that will fire every time a the specific message is received.
SysexReportFirmwareListener firmwareListener = new SysexReportFirmwareListener() {
    @Override
    public void messageReceived(SysexReportFirmwareMessage message) {
        System.out.println(message.getFirmwareName());
        System.out.println(message.getMajorVersion());
        System.out.println(message.getMinorVersion());
    }
};

// Somewhere in your application you decide to tell the Firmata library that it should rout the firmware messages to your new listener.
//   Listeners can be added or removed while the library is started or stopped.
firmata.addMessageListener(firmwareListener);

// Somewhere in your application you ask the project board to send its firmware name and version to us.
//   You do this by sending a ReportFirmware 'sysex' firmata message to the board.
//   Its response will be handled by your new listener above.
firmata.sendMessage(new SysexReportFirmwareMessage());

// At some point you do not care to respond to or handle firmware name messages being passed by the project board.
//   So you remove your listener to tell the library you wish to ignore these messages now.
firmata.removeMessageListener(firmwareListener);

// At some point you are done talking over the serial port, so you decide to shut down the Firmata library.
firmata.stop();
```


## Sending Messages
Any message that implements the abstract TransmittableMessage class can be transmitted through the serial port. This base class supports the serialization methods needed to convert your data message into a byte stream. For the base Firmata Protocol, these messages have been predefined for you.
```java
// Ask the project board to start eventing the analog (adc) values of analog pin 3.
firmata.sendMessage(new ReportAnalogPinMessage(3, true));
// Ask the project board to stop eventing the analog (adc) values of analog pin 3.
firmata.sendMessage(new ReportAnalogPinMessage(3, false));
// Ask the project board to reboot.
firmata.sendMessage(new SystemResetMessage());
```

## Receiving Messages
FiloFirmata has a parsing system that builds message objects up from a byte stream sent by the serial port. When a message is identified and built up, there is a system in place to then route this message to any bit of code that is interested in reading and handling its data. This is done through an event listening design. To handle a message sent up by the project board, add an event listener implementation for the message. The listener can be added and removed as necessary.

Some messages use a 'Channel' byte to identify the pin the message represents (See the analog/digital pin reporting mechanism in the Firmata protocol). To listen to messages for a specific channel, add your listener with an identifier indicating witch pin or port you want the listener to handle. To listen to messages from all channels that the message type supports, do not provide an identifier.
```java
// Handle analog messages from pin 3 evented to us from the project board (pin 3 request is handled below);
AnalogMessageListener analogListener = new AnalogMessageListener() {
    @Override
    public void messageReceived(AnalogMessage message) {
        //  Only deal with messages when the analog reading is higher than 100 (between 100-255 max analog adc value)
        //     Example would be some sensor that maybe doesn't send analog values below 100 or values below 100 are considered noise, etc.
        if (message.getAnalogValue() > 100) {
            System.out.println("Pin ADC value is greater than 100");
        }
    }
};

// Only listen to analog message events that correspond to analog pin/channel 3 on the project board (0 indexed).
firmata.addMessageListener(2, analogListener);
```

## Transmitting Custom Messages
Implementing your own custom Firmata messages into the FiloFirmata library is quite simple, and desired! Simply pick the base class you need for your type of message and implement the serialize callback to translate your message into a byte stream.

For example, if we want to implement some custom Firmata command that sends two separate strings of data to the Arduino / project board, we need to write the class and we need to pick a command byte that the Firmata protocol is not using. For this example, lets pick the byte "0x20". Please be weary of Firmata/MIDI protocol design requirements when picking your command byte. The two strings will be separated by a proprietary separator byte that when the project board sees, will know to start building the second string. Lets use "0xFF", since the strings can only be within 0x00-0x7f by using the 'two seven bit byte' Firmata base design for transmitting complex data. 
```java
public class TwoStringTransmitMessage extends TransmittableMessage {
    private String string1;
    private String string2;

    public TwoStringMessage(String string1, String string2) {
        // Tell the FiloFirmata library that our custom message is using Firmata command byte "0x20"
        super((byte) 0x20);
        this.string1 = string1;
        this.string2 = string2;
    }

    @Override
    protected Boolean serialize(ByteArrayOutputStream outputStream) {
        try {
            // Convert the java strings to a "two 7 bit byte" array
            //   See Firmata Protocol documentation.
            //   (avoids accidentally sending Firmata commands in data)
            byte[] string1Bytes = DataTypeHelpers.encodeTwoSevenBitByteSequence(string1);
            byte[] string2Bytes = DataTypeHelpers.encodeTwoSevenBitByteSequence(string2);
            outputStream.write(string1Bytes);
            outputString.write((byte) 0xFF);
            outputStream.write(string2Bytes);
            outputString.write((byte) 0xFF);
            return true;
        } catch (IOException e) {
            System.out.println("Message not built! Unable to convert strings to byte array!");
            return false;
        }
    }
}

// Send a Two String Message to our project board
firmata.sendMessage(new TwoStringMessage("This is String 1", "This is String 2"));
```

## Receiving Custom Messages
To implement a handler and custom message that is coming from your Arduino or project board, several classes must be built up and then registered within the FiloFirmata library. Whenever a registered command byte is identified over the serial port, a message builder for the command will be called which will parse the stream into a Message object. To handle, parse, and pass a custom message from the board, you will need to write 3 classes: a Message containing the data that was parsed from the custom command, a Builder to parse the message up from the serial port byte stream, and a Listener to handle processing of the message in your application once it has been received and built up. The builder class must be registered with the corresponding command byte to let the FiloFirmata library know that any message passed up from the project board with your custom command byte needs to be parsed by your custom message parser.


For the example, we will use the same Two String Message command above, however now we will be reading two strings from the project board, instead of sending them.

Define a TwoStringReceieve message that represents two string values sent by the project board.
```java
public class TwoStringReceiveMessage implements Message {
    private String string1;
    private String string2;

    public TwoStringReceiveMessage(String string1, String string2) {
        this.string1 = string1;
        this.string2 = string2;
    }

    public String getString1() {
        return string1;
    }

    public String getString2() {
        return string2;
    }
}
```

Define a message listener supporting the TwoStringReceiveMessage type
```java
public abstract class TwoStringReceiveListener extends MessageListener<TwoStringReceiveMessage> {

    // For translating the message dynamically, also support the
    //   TwoStringReceieveMessage class type in a runtime parameter. (For internal library workings)
    public TwoStringReceiveListener() {
        super(TwoStringReceiveMessage.class);
    }

}
```

Create a MessageBuilder that will be called whenever the the command byte (0x20) for the message is identified by the FiloFirmata library.
```java
public class TwoStringReceiveBuilder extends MessageBuilder {

    // Handle all messages sent by the project board using command byte "0x20".
    public TwoStringReceiveBuilder() {
        super((byte) 0x20);
    }

    // Note: Do not use scanners or read more from the stream than needed to parse your message,
    //   else you may corrupt the next message in the stream.
    @Override
    public Message buildMessage(Byte pinByte, InputStream inputStream) {
        // pinByte ignored as this is not a channel / pin based Firmata message

        // Allocate a byte buffer to build the two strings up.
        ByteBuffer stringBuffer = ByteBuffer.allocate(32);

        // Strings array to hold the two strings once they have been built up.
        String[] stringsArray = new String[2];

        // Two iterations for two strings
        for (int x = 0; x < 2; x++) {
            int currentByte;
            // Read every byte and only break if the strings are done, or if the stream closes.
            while (true) {
                try {
                    currentByte = inputStream.read();
                } catch (IOException e) {
                    // Error reading from stream
                    return null;
                }

                if (currentByte == 0xFF) {
                    try {
                        // String is done being built. Convert and save as a Java String object.
                        stringsArray[x] = DataTypeHelpers.decodeTwoSevenBitByteString(stringBuffer.array());
                    } catch (UnsupportedEncodingException e) {
                        // Error converting string from 'two 7 bit byte' Firmata protocol design.
                        return null;
                    }
                    // Reset the buffer to prepare for the next string.
                    stringBuffer.clear();
                    // Start building the other string, or break out of the final loop.
                    break;
                } else if (currentByte == -1) {
                    // Stream read error
                    return null;
                } else {
                    // The byte is part of string 1 or string 2. Add to buffer.
                    stringBuffer.put((byte) currentByte);
                }
            }
        }

        // Create a new instance of a TwoStringReceive message and return it to the FiloFirmata library.
        return new TwoStringReceiveMessage(stringsArray[0], stringsArray[1]);
    }
}
```

Register the TwoStringReceieve message builder statically in our project (or anywhere you wish)
```java
static {
    Firmata.addCustomCommandParser(new TwoStringReceiveBuilder());
}
```

## Transmitting & Receiving Custom SYSEX Messages
Designing a custom SYSEX message is very similar to designing a custom message above. It is suggested you use SYSEX for custom messages in general as it has a dedicated set of bytes for custom messages that the Firmata protocol has promised to not implement internally.

Please see the code base for examples of implementing your own custom SYSEX message. 



### Contributions Welcome!
