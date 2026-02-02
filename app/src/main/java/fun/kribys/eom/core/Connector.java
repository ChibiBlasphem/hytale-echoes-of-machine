package fun.kribys.eom.core;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Serializable data for connector
 */
public class Connector {
    public static final BuilderCodec<Connector> CODEC;
    public static final ArrayCodec<Connector> ARRAY_CODEC;

    public Connector() {
    }

    public Connector(String id, String world, Vector3i position) {
        this.id = id;
        this.world = world;
        this.position = position;
    }

    private String id;
    private String world;
    private Vector3i position;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorld() {
        return this.world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public Vector3i getPosition() {
        return this.position;
    }

    public void setPosition(Vector3i position) {
        this.position = position;
    }

    static {
        CODEC = BuilderCodec.builder(Connector.class, Connector::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (conn, val) -> conn.id = val, (conn) -> conn.id).add()
                .append(new KeyedCodec<>("World", Codec.STRING), (conn, val) -> conn.world = val, (conn) -> conn.world).add()
                .append(new KeyedCodec<>("Position", Vector3i.CODEC), (conn, val) -> conn.position = val, (conn) -> conn.position).add()
                .build();
        ARRAY_CODEC = new ArrayCodec<>(CODEC, Connector[]::new);
    }
}
