package com.breakersoft.plow.rndaemon;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;

import com.breakersoft.plow.Defaults;
import com.breakersoft.plow.Proc;
import com.breakersoft.plow.exceptions.RndClientExecuteException;
import com.breakersoft.plow.rnd.thrift.RndNodeApi;
import com.breakersoft.plow.rnd.thrift.RunTaskCommand;

public class RndClient {

    private static final Logger logger =
            org.slf4j.LoggerFactory.getLogger(RndClient.class);

    private final String host;
    private final int port;

    private TSocket socket;
    private TTransport transport;
    private TProtocol protocol;

    public RndClient(String host) {
        this.host = host;
        this.port = 11338;
    }

    public RndNodeApi.Client connect() throws TTransportException {
        socket = new TSocket(host, port);
        socket.setTimeout(Defaults.RND_CLIENT_SOCKET_TIMEOUT_MS);
        transport = new TFramedTransport(socket);
        protocol = new TBinaryProtocol(transport);
        transport.open();
        return new RndNodeApi.Client(protocol);
    }

    //TODO change to runTask
    public void runProcess(RunTaskCommand command) {
        try {
            connect().runTask(command);
        } catch (TException e) {
            logger.warn("Failed to run task " + command, e);
            throw new RndClientExecuteException(e);
        }
        finally {
            socket.close();
        }
    }

    public void kill(Proc proc, String reason) {
        try {
            connect().killRunningTask(proc.getProcId().toString(), reason);
        } catch (TException e) {
            logger.warn("Failed to kill proc " + proc, e);
            throw new RndClientExecuteException(e);
        }
        finally {
            socket.close();
        }
    }
}
