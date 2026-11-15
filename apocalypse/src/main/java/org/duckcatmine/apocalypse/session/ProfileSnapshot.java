package org.duckcatmine.apocalypse.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Everything a profile swap must carry; items are Paper's version-upgradable byte form. */
public record ProfileSnapshot(byte[] items, int level, float exp, int totalExperience, double health, int food,
                              float saturation, float exhaustion, int fireTicks, int air, List<Effect> effects,
                              String world, double x, double y, double z, float yaw, float pitch, String gameMode) {

    private static final int FORMAT = 1;

    public record Effect(String key, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
    }

    public ProfileSnapshot {
        effects = List.copyOf(effects);
    }

    public byte[] encode() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeInt(items.length);
            out.write(items);
            out.writeInt(level);
            out.writeFloat(exp);
            out.writeInt(totalExperience);
            out.writeDouble(health);
            out.writeInt(food);
            out.writeFloat(saturation);
            out.writeFloat(exhaustion);
            out.writeInt(fireTicks);
            out.writeInt(air);
            out.writeInt(effects.size());
            for (Effect effect : effects) {
                out.writeUTF(effect.key());
                out.writeInt(effect.duration());
                out.writeInt(effect.amplifier());
                out.writeBoolean(effect.ambient());
                out.writeBoolean(effect.particles());
                out.writeBoolean(effect.icon());
            }
            out.writeUTF(world);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yaw);
            out.writeFloat(pitch);
            out.writeUTF(gameMode);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProfileSnapshot decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int format = in.readInt();
            if (format != FORMAT) throw new IOException("Format de profil inconnu : " + format);
            byte[] items = new byte[in.readInt()];
            in.readFully(items);
            int level = in.readInt();
            float exp = in.readFloat();
            int total = in.readInt();
            double health = in.readDouble();
            int food = in.readInt();
            float saturation = in.readFloat();
            float exhaustion = in.readFloat();
            int fire = in.readInt();
            int air = in.readInt();
            int count = in.readInt();
            List<Effect> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                effects.add(new Effect(in.readUTF(), in.readInt(), in.readInt(), in.readBoolean(), in.readBoolean(), in.readBoolean()));
            }
            return new ProfileSnapshot(items, level, exp, total, health, food, saturation, exhaustion, fire, air, effects,
                    in.readUTF(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readUTF());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
