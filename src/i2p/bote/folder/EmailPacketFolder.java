/**
 * Copyright (C) 2009  HungryHobo@mail.i2p
 * 
 * The GPG fingerprint for HungryHobo@mail.i2p is:
 * 6DD3 EAA2 9990 29BC 4AD2 7486 1E2C 7B61 76DC DC12
 * 
 * This file is part of I2P-Bote.
 * I2P-Bote is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * I2P-Bote is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with I2P-Bote.  If not, see <http://www.gnu.org/licenses/>.
 */

package i2p.bote.folder;

import i2p.bote.UniqueId;
import i2p.bote.network.PacketListener;
import i2p.bote.packet.CommunicationPacket;
import i2p.bote.packet.EmailPacketDeleteRequest;
import i2p.bote.packet.EncryptedEmailPacket;
import i2p.bote.packet.dht.DhtStorablePacket;

import java.io.File;

import net.i2p.data.Destination;
import net.i2p.data.Hash;
import net.i2p.util.Log;

/**
 * A subclass of {@link DhtPacketFolder} that stores email packets and deletes them
 * upon {@link EmailPacketDeleteRequest}s.
 */
public class EmailPacketFolder extends DhtPacketFolder<EncryptedEmailPacket> implements PacketListener, ExpirationListener {
    private Log log = new Log(EmailPacketFolder.class);

    public EmailPacketFolder(File storageDir) {
        super(storageDir);
    }

    /** Overridden to set a time stamp on the packet */
    @Override
    public void store(DhtStorablePacket packetToStore) {
        if (packetToStore instanceof EncryptedEmailPacket) {
            ((EncryptedEmailPacket)packetToStore).setStoreTime(System.currentTimeMillis());
            super.store(packetToStore);
        }
        else
            log.error("Not storing packet of type " + (packetToStore==null?"<null>":packetToStore.getClass().getSimpleName()) + " in EmailPacketFolder.");
    }
    
    /** Overridden to erase the time stamp because there is no need for other peers to see it. */
    @Override
    public DhtStorablePacket retrieve(Hash dhtKey) {
        DhtStorablePacket packet = super.retrieve(dhtKey);
        if (!(packet instanceof EncryptedEmailPacket)) {
            log.error("Packet of type " + (packet==null?"<null>":packet.getClass().getSimpleName()) + " found in " + getClass().getSimpleName());
            return null;
        }
        else {
            EncryptedEmailPacket emailPacket = (EncryptedEmailPacket)packet;
            emailPacket.setStoreTime(0);
            return emailPacket;
        }
    }
    
    /** Handles delete requests */
    @Override
    public void packetReceived(CommunicationPacket packet, Destination sender, long receiveTime) {
        if (packet instanceof EmailPacketDeleteRequest) {
            EmailPacketDeleteRequest delRequest = (EmailPacketDeleteRequest)packet;
            
            // see if the packet exists
            Hash dhtKey = delRequest.getDhtKey();
            DhtStorablePacket storedPacket = retrieve(dhtKey);
            if (storedPacket instanceof EncryptedEmailPacket) {
                // verify
                Hash expectedHash = ((EncryptedEmailPacket)storedPacket).getDeleteVerificationHash();
                UniqueId delAuthorization = delRequest.getAuthorization();
                Hash actualHash = new Hash(delAuthorization.toByteArray());
                boolean valid = actualHash.equals(expectedHash);
            
                if (valid)
                    delete(dhtKey);
                else
                    log.debug("Invalid Delete Authorization in EmailPacketDeleteRequest. Should be: <" + expectedHash.toBase64() + ">, is <" + actualHash.toBase64() +">");
            }
            else
                log.debug("EncryptedEmailPacket expected for DHT key <" + dhtKey + ">, found " + storedPacket.getClass().getSimpleName());
        }
    }
    
    @Override
    public synchronized void deleteExpired() {
        long currentTimeMillis = System.currentTimeMillis();
        File[] files = getFilenames();
        for (File file: files)
            try {
                EncryptedEmailPacket emailPacket = createFolderElement(file);
                if (currentTimeMillis > emailPacket.getStoreTime() + EXPIRATION_TIME_MILLISECONDS) {
                    log.debug("Deleting expired email packet: <" + file.getAbsolutePath() + ">");
                    if (!file.delete())
                        log.error("Can't delete file: <" + file.getAbsolutePath() + ">");
                }
            }
            catch (Exception e) {
                log.error("Can't create af EmailPacket from file: " + file.getAbsolutePath(), e);
            }
    }
}