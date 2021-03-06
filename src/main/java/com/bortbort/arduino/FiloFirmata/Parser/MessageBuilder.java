package com.bortbort.arduino.FiloFirmata.Parser;

import com.bortbort.arduino.FiloFirmata.Messages.Message;
import java.io.InputStream;

/**
 * MessageBuilder definition for building Firmata Message objects from a SerialPort's InputStream.
 */
public abstract class MessageBuilder extends MessageBuilderBase {

    /**
     * Construct a MessageBuilder using the given CommandByte.
     *
     * @param commandByte CommandBytes enum value representing a Firmata command byte.
     */
    public MessageBuilder(CommandBytes commandByte) {
        super(commandByte.getCommandByte());
    }

    /**
     * Construct a MessageBuilder using the given commandByte.
     *
     * @param commandByte Byte value representing a Firmata command byte.
     */
    public MessageBuilder(byte commandByte) {
        super(commandByte);
    }

    /**
     * Build a Message from the given InputStream. Captures and removes the bytes needed to generate a
     * Firmata Message, which it then returns for eventing out to the client. The inputStream will
     * then be deferred back to the main parser automatically for scanning for new command bytes.
     *
     * @param channelByte channelByte indicating the pin/port to perform the command against.
     * @param inputStream SerialPort InputStream containing the serialized byte data representing the message.
     * @return Firmata Message object representing the data obtained from the bytes in the InputStream.
     */
    public abstract Message buildMessage(Byte channelByte, InputStream inputStream);

}
