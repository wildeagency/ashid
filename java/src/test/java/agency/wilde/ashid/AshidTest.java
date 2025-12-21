package agency.wilde.ashid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AshidTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("creates ashid without prefix")
        void createWithoutPrefix() {
            String id = Ashid.create();
            assertNotNull(id);
            assertEquals(22, id.length());
        }

        @Test
        @DisplayName("creates ashid with simple prefix")
        void createWithPrefix() {
            String id = Ashid.create("u");
            assertTrue(id.startsWith("u"));
            assertEquals(23, id.length());
        }

        @Test
        @DisplayName("creates ashid with underscore delimiter")
        void createWithUnderscoreDelimiter() {
            String id = Ashid.create("user_");
            assertTrue(id.startsWith("user_"));
        }

        @Test
        @DisplayName("creates ashid with dash delimiter (converts to underscore)")
        void createWithDashDelimiter() {
            String id = Ashid.create("user-");
            assertTrue(id.startsWith("user_"));
        }

        @Test
        @DisplayName("lowercases prefix")
        void lowercasesPrefix() {
            String id = Ashid.create("USER");
            assertTrue(id.startsWith("user"));
        }

        @Test
        @DisplayName("throws on invalid prefix with numbers")
        void throwsOnPrefixWithNumbers() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.create("user1"));
        }

        @Test
        @DisplayName("throws on invalid prefix with middle underscore")
        void throwsOnMiddleUnderscore() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.create("user_name"));
        }

        @Test
        @DisplayName("throws on negative timestamp")
        void throwsOnNegativeTimestamp() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.create(null, -1, 0));
        }

        @Test
        @DisplayName("throws on timestamp exceeding maximum")
        void throwsOnMaxTimestamp() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.create(null, 35184372088832L, 0));
        }

        @Test
        @DisplayName("throws on negative random")
        void throwsOnNegativeRandom() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.create(null, 0, -1));
        }

        @Test
        @DisplayName("generates unique IDs")
        void generatesUniqueIds() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(Ashid.create("u"));
            }
            assertEquals(1000, ids.size());
        }
    }

    @Nested
    @DisplayName("Fixed vs Variable Length Formats")
    class FormatTests {

        @Test
        @DisplayName("no prefix: always 22 chars")
        void noPrefixFixed22() {
            String id = Ashid.create(null, 1000, 1000);
            assertEquals(22, id.length());
        }

        @Test
        @DisplayName("prefix without underscore: 22-char base + prefix")
        void prefixWithoutUnderscoreFixed() {
            String id = Ashid.create("user", 1000, 1000);
            assertEquals(26, id.length()); // 4 + 22
            assertTrue(id.startsWith("user"));
        }

        @Test
        @DisplayName("prefix with underscore: variable length")
        void prefixWithUnderscoreVariable() {
            String id = Ashid.create("user_", 1000, 1000);
            assertTrue(id.startsWith("user_"));
            // Variable length, not necessarily 22 for base
        }

        @Test
        @DisplayName("timestamp 0 with underscore: omits timestamp")
        void timestampZeroWithUnderscore() {
            String id = Ashid.create("user_", 0, 1000);
            assertTrue(id.startsWith("user_"));
            // When timestamp is 0, it's omitted
            assertEquals(0, Ashid.timestamp(id));
        }

        @Test
        @DisplayName("timestamp 0 without underscore: pads to 22")
        void timestampZeroWithoutUnderscore() {
            String id = Ashid.create("u", 0, 1000);
            assertEquals(23, id.length()); // 1 + 22
            assertEquals(0, Ashid.timestamp(id));
        }
    }

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("parses ashid without prefix")
        void parseWithoutPrefix() {
            String id = Ashid.create(null, 1000, 2000);
            Ashid.ParseResult result = Ashid.parse(id);
            assertEquals("", result.getPrefix());
        }

        @Test
        @DisplayName("parses ashid with simple prefix")
        void parseWithPrefix() {
            String id = Ashid.create("user", 1000, 2000);
            Ashid.ParseResult result = Ashid.parse(id);
            assertEquals("user", result.getPrefix());
        }

        @Test
        @DisplayName("parses ashid with underscore delimiter")
        void parseWithUnderscoreDelimiter() {
            String id = Ashid.create("user_", 1000, 2000);
            Ashid.ParseResult result = Ashid.parse(id);
            assertEquals("user_", result.getPrefix());
        }

        @Test
        @DisplayName("parses ashid with dash (normalizes to underscore)")
        void parseWithDash() {
            // Create with underscore, parse with dash in raw string
            String idWithDash = "user-z80000000000z8";
            Ashid.ParseResult result = Ashid.parse(idWithDash);
            assertEquals("user_", result.getPrefix());
        }

        @Test
        @DisplayName("throws on empty string")
        void throwsOnEmpty() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.parse(""));
        }

        @Test
        @DisplayName("throws on prefix only")
        void throwsOnPrefixOnly() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.parse("user_"));
        }

        @Test
        @DisplayName("throws on wrong length without delimiter")
        void throwsOnWrongLength() {
            assertThrows(IllegalArgumentException.class, () -> Ashid.parse("user123"));
        }
    }

    @Nested
    @DisplayName("Extraction methods")
    class ExtractionTests {

        @Test
        @DisplayName("prefix() extracts prefix")
        void prefixExtraction() {
            assertEquals("user", Ashid.prefix(Ashid.create("user", 1000, 2000)));
            assertEquals("user_", Ashid.prefix(Ashid.create("user_", 1000, 2000)));
            assertEquals("", Ashid.prefix(Ashid.create(null, 1000, 2000)));
        }

        @Test
        @DisplayName("timestamp() extracts and roundtrips")
        void timestampRoundtrip() {
            long[] timestamps = {0, 1, 1000, System.currentTimeMillis(), 35184372088831L};
            for (long ts : timestamps) {
                String id = Ashid.create("u", ts, 1000);
                assertEquals(ts, Ashid.timestamp(id));
            }
        }

        @Test
        @DisplayName("random() extracts and roundtrips")
        void randomRoundtrip() {
            long[] randoms = {0, 1, 1000, 9007199254740991L};
            for (long r : randoms) {
                String id = Ashid.create("u", 1000, r);
                assertEquals(r, Ashid.random(id));
            }
        }
    }

    @Nested
    @DisplayName("normalize()")
    class NormalizeTests {

        @Test
        @DisplayName("converts uppercase to lowercase")
        void lowercases() {
            String id = Ashid.create("user", 1000, 2000);
            String upper = id.toUpperCase();
            assertEquals(id, Ashid.normalize(upper));
        }

        @Test
        @DisplayName("converts ambiguous characters")
        void convertsAmbiguous() {
            // Create an ID, then manually insert ambiguous chars
            String id = Ashid.create("u", 1000, 1);
            // The normalize function should handle I->1, O->0, L->1
            String withAmbiguous = id.replace('0', 'O').replace('1', 'I');
            String normalized = Ashid.normalize(withAmbiguous);
            assertEquals(Ashid.timestamp(id), Ashid.timestamp(normalized));
            assertEquals(Ashid.random(id), Ashid.random(normalized));
        }

        @Test
        @DisplayName("converts dash to underscore")
        void convertsDash() {
            String normalized = Ashid.normalize("user-z800000000000z8");
            assertTrue(normalized.startsWith("user_"));
        }
    }

    @Nested
    @DisplayName("isValid()")
    class IsValidTests {

        @Test
        @DisplayName("returns true for valid ashids")
        void validAshids() {
            assertTrue(Ashid.isValid(Ashid.create()));
            assertTrue(Ashid.isValid(Ashid.create("u")));
            assertTrue(Ashid.isValid(Ashid.create("user_")));
        }

        @Test
        @DisplayName("returns false for invalid strings")
        void invalidStrings() {
            assertFalse(Ashid.isValid(""));
            assertFalse(Ashid.isValid(null));
            assertFalse(Ashid.isValid("!!!"));
            assertFalse(Ashid.isValid("user123")); // wrong length
        }
    }

    @Nested
    @DisplayName("Time-sortability")
    class TimeSortabilityTests {

        @Test
        @DisplayName("IDs are lexicographically sortable by time")
        void lexicographicSort() {
            List<String> ids = new ArrayList<>();
            long baseTime = System.currentTimeMillis();

            // Create IDs with incrementing timestamps
            for (int i = 0; i < 10; i++) {
                ids.add(Ashid.create("u", baseTime + i * 1000, 0));
            }

            // Verify lexicographic order matches chronological order
            for (int i = 0; i < ids.size() - 1; i++) {
                assertTrue(ids.get(i).compareTo(ids.get(i + 1)) < 0,
                    "ID " + i + " should be less than ID " + (i + 1));
            }
        }

        @Test
        @DisplayName("Same timestamp, random breaks tie")
        void randomTiebreaker() {
            long time = System.currentTimeMillis();
            String id1 = Ashid.create("u", time, 100);
            String id2 = Ashid.create("u", time, 200);

            assertTrue(id1.compareTo(id2) < 0);
        }
    }

    @Nested
    @DisplayName("Double-click selectability")
    class SelectabilityTests {

        @Test
        @DisplayName("no hyphens in output")
        void noHyphens() {
            for (int i = 0; i < 100; i++) {
                String id = Ashid.create("user");
                assertFalse(id.contains("-"), "ID should not contain hyphens");
            }
        }

        @Test
        @DisplayName("no spaces in output")
        void noSpaces() {
            for (int i = 0; i < 100; i++) {
                String id = Ashid.create("user");
                assertFalse(id.contains(" "), "ID should not contain spaces");
            }
        }

        @Test
        @DisplayName("only alphanumeric and underscore")
        void onlyAlphanumericAndUnderscore() {
            for (int i = 0; i < 100; i++) {
                String id = Ashid.create("user_");
                assertTrue(id.matches("^[a-z0-9_]+$"), "ID should only contain lowercase alphanumeric and underscore");
            }
        }
    }
}
