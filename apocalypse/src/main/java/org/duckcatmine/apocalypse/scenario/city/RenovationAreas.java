package org.duckcatmine.apocalypse.scenario.city;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Explicit exclusions apply at every height. Invalid protection data blocks maintenance. */
public final class RenovationAreas {
    public record Area(String world, String label, int minX, int minZ, int maxX, int maxZ) {
        public Area {
            if (world == null || !world.matches("[A-Za-z0-9_-]{1,80}") || label == null || label.isBlank() || label.length()>100
                    || minX>maxX || minZ>maxZ || Math.abs((long)minX)>30000000 || Math.abs((long)minZ)>30000000
                    || Math.abs((long)maxX)>30000000 || Math.abs((long)maxZ)>30000000)
                throw new IllegalArgumentException("Zone préservée invalide.");
        }
        public boolean intersects(String world, int x1,int z1,int x2,int z2) {
            return this.world.equals(world) && minX<=x2 && maxX>=x1 && minZ<=z2 && maxZ>=z1;
        }
        public boolean contains(String world,int x,int z) { return intersects(world,x,z,x,z); }
    }
    private RenovationAreas() {}
    public static List<Area> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        try {
            if(Files.size(file)>131072) throw new IllegalArgumentException("Trop de zones préservées.");
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(json.get("version").getAsInt()!=1 || json.getAsJsonArray("areas").size()>128) throw new IllegalArgumentException("Format de protection inconnu.");
            List<Area> areas=new ArrayList<>();
            for(var element:json.getAsJsonArray("areas")) {
                var a=element.getAsJsonObject();
                for(String key:List.of("minX","minZ","maxX","maxZ")) {
                    double number=a.get(key).getAsDouble();
                    if(!Double.isFinite(number) || number!=Math.rint(number) || Math.abs(number)>30000000) throw new IllegalArgumentException("Coordonnée invalide.");
                }
                areas.add(new Area(a.get("world").getAsString(),a.get("label").getAsString(),a.get("minX").getAsInt(),a.get("minZ").getAsInt(),a.get("maxX").getAsInt(),a.get("maxZ").getAsInt()));
            }
            return List.copyOf(areas);
        } catch(RuntimeException e) { throw new IOException("Protection de rénovation illisible : travaux interdits.",e); }
    }
}
