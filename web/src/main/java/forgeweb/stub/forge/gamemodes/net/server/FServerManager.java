package forgeweb.stub.forge.gamemodes.net.server;

import forge.gamemodes.match.input.InputSynchronized;
import forge.gamemodes.net.event.NetEvent;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gamemodes.net.server.RemoteClient;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.interfaces.IDraftEventHandler;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.ILobbyListener;
import forge.player.PlayerControllerHuman;
import forge.util.IHasForgeLog;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * No hosting on web (no sockets). Single-player code only asks "are we hosting?",
 * which is always false here; that keeps Netty and UPnP out of the build.
 */
public final class FServerManager implements IHasForgeLog {
    private static FServerManager instance;

    public static FServerManager getInstance() {
        if (instance == null) instance = new FServerManager();
        return instance;
    }

    public static String getLocalAddress() { return null; }
    public static LinkedHashMap<String, String> getAllLocalAddresses() { return new LinkedHashMap<>(); }
    public static String getExternalAddress() { return null; }

    public void startServer(int port) { throw new UnsupportedOperationException("Hosting is not available on web"); }
    public void stopServer() { }
    public boolean isHosting() { return false; }
    public boolean isUPnPMapped() { return false; }
    public boolean isMatchActive() { return false; }
    public int getTotalSendErrors() { return 0; }
    public forge.gamemodes.net.NetworkByteTracker getByteTracker() { return null; }
    public RemoteClient getClientBySlotIndex(int slotIndex) { return null; }
    public RemoteClient findClientByIndex(int index) { return null; }
    public IGuiGame getGui(int index) { return null; }
    public void sendToSlot(int slotIndex, NetEvent event) { }
    public void broadcast(NetEvent event) { }
    public void broadcastExcept(NetEvent event, RemoteClient notTo) { }
    public void broadcastExcept(NetEvent event, Collection<RemoteClient> notTo) { }
    public String formatAfkTimeoutMessage() { return ""; }
    public forge.gamemodes.net.server.FServerManager.AfkTimeout armAfkTimeout(PlayerControllerHuman controller, InputSynchronized input) { return null; }
    public void setLobby(ServerGameLobby lobby) { }
    public void unsetReady() { }
    public void setLobbyListener(ILobbyListener listener) { }
    public void setDraftHandler(IDraftEventHandler handler) { }
    public void updateLobbyState() { }
    public void updateSlot(int index, UpdateLobbyPlayerEvent event) { }
    public void clearPlayerGuis() { }
    public boolean handleCommand(String messageText) { return false; }
    public void convertToAI(RemoteClient client) { }
}
