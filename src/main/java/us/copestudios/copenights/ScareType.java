package us.copestudios.copenights;

import java.util.Locale;

public enum ScareType {
    HEROBRINE("Herobrine appears in the distance, stares, then vanishes"),
    STALKER("Herobrine stands right behind them until they turn around"),
    JUMPSCARE("Elder guardian face + scream + title"),
    SOUNDS("A random creepy sound right behind them"),
    FOOTSTEPS("Footsteps creeping up from behind"),
    CREEPER("Creeper hiss in their ear (no explosion)"),
    WHISPER("A whisper in chat from Herobrine"),
    BLACKOUT("Blinded in the dark with a heartbeat... then a roar"),
    LIGHTNING("Harmless lightning strike next to them (others nearby see it)"),
    SKY_DROP("Yanked 100 blocks into the sky, free fall... then back on the ground"),
    BURIED("Gravel caves in around them for a few seconds (only they see it)"),
    LIGHTS_OUT("Nearby torches and lanterns blow out one by one (only they see it)"),
    FAKE_TNT("Lit TNT appears behind them and 'explodes' (no damage)"),
    TURN_AROUND("Their camera spins around... Herobrine is right there"),
    FAKE_JOIN("'Herobrine joined the game', a message, then he leaves"),
    KNOCK("Knocking on the nearest door, then it creaks open by itself"),
    SURROUNDED("Mob groans from every side closing in, then a breath behind them"),
    PHANTOM_HIT("Something invisible hits them 3 times and laughs (no damage)"),
    RANDOM("Picks one at random");

    private final String description;

    ScareType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public static ScareType parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
