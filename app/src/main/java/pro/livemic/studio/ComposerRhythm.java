package pro.livemic.studio;
import java.util.*;

/**
 * Enhanced expressive rhythm grooves for pop, rock, r&b, ballad, waltz, and bossa nova.
 * Standard General MIDI drum notes:
 * 36: Bass Drum, 37: Side Stick, 38: Snare, 40: Electric Snare
 * 42: Closed Hi-Hat, 44: Pedal Hi-Hat, 46: Open Hi-Hat
 * 45: Low Tom, 47: Mid Tom, 50: High Tom, 49: Crash Cymbal, 51: Ride Cymbal
 */
final class ComposerRhythm {
    static final class Hit {
        final double beat, gain;
        final int note;
        Hit(double b, int n, double g) { beat = b; note = n; gain = g; }
    }

    static List<Hit> drums(int rhythm, boolean fill) {
        return drums(rhythm, fill, 0, .60);
    }

    static List<Hit> drums(int rhythm, boolean fill, int barIndex, double energy) {
        List<Hit> hits = new ArrayList<>();
        double length = (rhythm == 6 || rhythm == 7) ? 3 : 4;

        // Crash cymbal accent on 1st beat of every 4th bar or song start
        if (barIndex % 4 == 0 && !fill) {
            hits.add(new Hit(0, 49, Math.min(1.0, .58 + energy * .3)));
        }

        // 1. Hi-Hat & Cymbal grooves
        if (rhythm == 2) {
            // Shuffle / Swing: Triplet feel (beat, beat + 2/3)
            for (int i = 0; i < 4; i++) {
                if (fill && i >= length - 1) continue;
                hits.add(new Hit(i, 42, .46 + energy * .15));
                hits.add(new Hit(i + 2. / 3, 42, .28 + energy * .12));
                // occasional open hi-hat on the offbeat
                if (i == 3 && barIndex % 2 == 1 && energy > .4) {
                    hits.add(new Hit(i + 2. / 3, 46, .45));
                }
            }
        } else if (rhythm == 5) {
            // Bossa Nova: Steady closed hat / ride cymbal with subtle variations
            for (double beat = 0; beat < length; beat += .5) {
                if (fill && beat >= length - 1) continue;
                int n = (int) Math.round(beat * 2);
                hits.add(new Hit(beat, energy > .7 ? 51 : 42, n % 2 == 0 ? .36 : .22));
            }
        } else if (rhythm == 6) {
            // Waltz 3/4: Hi-hat on beats with pedal hat accent
            for (int i = 0; i < 3; i++) {
                if (fill && i >= length - 1) continue;
                hits.add(new Hit(i, i == 0 ? 44 : 42, i == 0 ? .40 : .26));
            }
        } else if (rhythm == 7) {
            // 6/8 Slow Ballad: 8th-note pulse with ride/hat
            for (double beat = 0; beat < 3; beat += .5) {
                if (fill && beat >= length - 1) continue;
                int n = (int) Math.round(beat * 2);
                hits.add(new Hit(beat, energy > .5 ? 51 : 42, n == 0 || n == 3 ? .45 : .22));
            }
        } else {
            // 8-beat, 16-beat, R&B, Rock
            double step = (rhythm == 1 || rhythm == 3) ? .25 : .5;
            for (double beat = 0; beat < length; beat += step) {
                if (fill && beat >= length - 1) continue;
                int i = (int) Math.round(beat / step);
                if (step == .25 && i % 2 == 1 && energy < .45) continue;

                // Open hi-hat accents before snare or bar transition
                boolean openHat = (rhythm == 4 && i % 4 == 3)
                        || (rhythm == 1 && (i == 7 || i == 15) && energy > .5)
                        || (rhythm == 3 && i == 11 && energy > .4);

                int cymbalNote = openHat ? 46 : (rhythm == 4 && energy > .75 && barIndex % 4 >= 2) ? 51 : 42;
                double gain = openHat ? .52 : (i % 2 == 0 ? .44 + energy * .1 : .24);
                hits.add(new Hit(beat, cymbalNote, gain));
            }
        }

        // 2. Kick Drum patterns
        double[] kicks = kicks(rhythm, barIndex);
        for (double beat : kicks) {
            if (!fill || beat < length - 1) {
                hits.add(new Hit(beat, 36, .82 + energy * .1));
            }
        }

        // 3. Snare & Side Stick patterns
        if (rhythm == 5) {
            // Bossa Nova Brazilian clave side stick: 0, 1.5, 2.5, 3.25
            double[] clave = {0, 1.5, 2.5, 3.25};
            for (double beat : clave) {
                if (!fill || beat < length - 1) hits.add(new Hit(beat, 37, .65));
            }
        } else if (rhythm == 6) {
            // Waltz: Snare on 2 and 3
            if (!fill) {
                hits.add(new Hit(1.0, 38, .48));
                hits.add(new Hit(2.0, 38, .42));
            }
        } else if (rhythm == 7) {
            // 6/8 Ballad: Strong backbeat on beat 1.5
            if (!fill) hits.add(new Hit(1.5, 38, .74));
        } else {
            // Standard backbeats (beats 1.0 and 3.0 in 4/4)
            double[] snares = {1.0, 3.0};
            for (double beat : snares) {
                if (!fill || beat < length - 1) {
                    hits.add(new Hit(beat, 38, .76 + energy * .1));
                }
            }
            // Ghost snare notes for funk / R&B groove
            if (!fill && (rhythm == 1 || rhythm == 3) && energy > .35) {
                hits.add(new Hit(2.75, 38, .22));
                if (energy > .6) hits.add(new Hit(1.75, 38, .18));
            }
        }

        // 4. Ending Fill-in (마무리 필인)
        if (fill) {
            hits.add(new Hit(length - .75, 38, .52)); // Snare
            hits.add(new Hit(length - .50, 50, .64)); // High Tom
            hits.add(new Hit(length - .25, 47, .74)); // Mid Tom
            hits.add(new Hit(length - .125, 45, .68)); // Low Tom
        }

        return hits;
    }

    /** Track articulation over the part groove; pattern zero preserves legacy events. */
    static List<Hit> drums(int rhythm, boolean fill, int barIndex, double energy, int pattern) {
        List<Hit> base=drums(rhythm,fill,barIndex,energy);
        if(pattern<=0||pattern>4)return base;
        double length=rhythm==6||rhythm==7?3:4;
        List<Hit> hits=new ArrayList<>();
        for(Hit h:base){
            boolean cymbal=h.note==42||h.note==44||h.note==46||h.note==49||h.note==51;
            // The closing fill retains its timing, toms and dynamics in every kit pattern.
            if(fill&&h.beat>=length-1){hits.add(h);continue;}
            if(pattern==1){
                if(cymbal){if(h.note==49||Math.abs(h.beat-Math.rint(h.beat))>1e-6)continue;hits.add(new Hit(h.beat,42,h.gain*.72));}
                else hits.add(new Hit(h.beat,h.note,h.gain*.8));
            }else if(pattern==3){hits.add(new Hit(h.beat,cymbal?51:h.note,h.gain));}
            else if(pattern==4){
                if(h.note==36){if(h.beat==0)hits.add(new Hit(h.beat,36,h.gain*.75));}
                else if(h.note==38||h.note==37) {if(h.gain>=.4)hits.add(new Hit(h.beat,37,h.gain*.65));}
            }else hits.add(h);
        }
        if(pattern==2){
            // Driving eighth-note kick pulse, retaining the groove's syncopated kicks.
            for(double at=0;at<(fill?length-1:length);at+=.5){
                boolean exists=false;for(Hit h:hits)if(h.note==36&&Math.abs(h.beat-at)<1e-6){exists=true;break;}
                if(!exists)hits.add(new Hit(at,36,.70+energy*.15));
            }
        }
        return hits;
    }

    static double[] kicks(int rhythm, int barIndex) {
        switch (rhythm) {
            case 1: // Pop 16-beat
                return barIndex % 4 == 3
                        ? new double[]{0, 1.75, 2.5, 3.5}
                        : new double[]{0, 1.75, 2.5};
            case 2: // Shuffle
                return barIndex % 2 == 1
                        ? new double[]{0, 1.0 + 2. / 3, 2.0}
                        : new double[]{0, 2.0};
            case 3: // R&B / Neo-Soul
                return barIndex % 4 == 3
                        ? new double[]{0, 1.5, 2.5, 3.25}
                        : new double[]{0, 1.75, 2.5};
            case 4: // Rock
                return new double[]{0, .5, 2.0, 2.5};
            case 5: // Bossa Nova
                return new double[]{0, 1.5, 2.0, 3.5};
            case 6: // Waltz
                return new double[]{0};
            case 7: // 6/8 Ballad
                return barIndex % 2 == 1
                        ? new double[]{0, 1.0, 2.0}
                        : new double[]{0, 2.0};
            case 8: // Hip-hop / Boom bap / Trap
                return barIndex % 2 == 1
                        ? new double[]{0, 1.25, 2.5, 3.25}
                        : new double[]{0, 1.5, 2.75};
            default: // Pop 8-beat
                return barIndex % 4 == 3
                        ? new double[]{0, 2.0, 3.5}
                        : new double[]{0, 2.0};
        }
    }

    static double[] strums(int rhythm) {
        switch (rhythm) {
            case 1: return new double[]{0, .75, 1.25, 1.75, 2.0, 2.75, 3.25, 3.75};
            case 2: return new double[]{0, 2. / 3, 1.0, 1.0 + 2. / 3, 2.0, 2.0 + 2. / 3, 3.0, 3.0 + 2. / 3};
            case 3: return new double[]{0, .75, 1.5, 2.5, 3.25};
            case 4: return new double[]{0, .5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
            case 5: return new double[]{0, .75, 1.5, 2.5, 3.25};
            case 6: return new double[]{0, 1.0, 2.0}; // Waltz
            case 7: return new double[]{0, 1.0, 1.5, 2.5}; // 6/8 Ballad
            case 8: return new double[]{0, 1.25, 2.0, 2.75}; // Hip-hop
            default: return new double[]{0, 1.0, 1.5, 2.5, 3.0, 3.5};
        }
    }
}
