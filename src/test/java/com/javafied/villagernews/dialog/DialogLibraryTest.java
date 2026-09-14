package com.javafied.villagernews.dialog;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Against the dialogs the converter extracted from the real add-on (dev/converted, gitignored). */
class DialogLibraryTest {
	private static DialogLibrary library;

	@BeforeAll
	static void load() throws IOException {
		Path file = Path.of("dev/converted/server/dialogs.json");
		assumeTrue(Files.exists(file), "no converted add-on");
		library = DialogLibrary.load(file);
	}

	@Test
	void extractsEveryDialog() {
		assertEquals(523, library.size());
		assertFalse(library.hurtSounds(false).isEmpty());
		assertFalse(library.hurtSounds(true).isEmpty());
		for (List<String> conversation : library.conversations()) {
			for (String part : conversation) {
				assertNotNull(library.get(part), "conversation part " + part);
			}
		}
	}

	@Test
	void evaluatesComputedCooldowns() {
		// Script: csiavd:{didrid:2*(jtzadj.csiavd.didrid??0)} with jtzadj.csiavd.didrid = 80.
		assertEquals(160, library.get("vgysma").entityCooldown().same());
		assertEquals(10, library.get("vgysma").entityCooldown().any(), "unset half keeps its default");
		DialogLibrary.Line first = library.get("kxrhxt").lines().getFirst();
		assertEquals(2, first.subtitles().size());
		assertEquals(0.87, first.subtitles().get(1).time(), 1e-9);
	}

	/** Every dialog id the hand-ported triggers use must exist in the add-on. */
	@Test
	void portedTriggersReferenceRealDialogs() throws ReflectiveOperationException {
		List<String> ids = new ArrayList<>();
		for (Class<?> reactions : List.of(VillagerReactions.class, VillagerItemReactions.class, TradeReactions.class,
				PlayerActionReactions.class, VillagerLifeReactions.class, BlockUseReactions.class)) {
			for (Field field : reactions.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) || field.getName().startsWith("TAG_") || field.getName().startsWith("ITEM_")
						|| field.getName().equals("NOSED_CONVERSATIONS") || field.getName().equals("TRADE_DIALOGS")) {
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value instanceof String id && !id.contains(":") && !id.equals("none")) {
					ids.add(id);
				} else if (value instanceof java.util.Map<?, ?> map) {
					map.values().stream().filter(String.class::isInstance).map(String.class::cast).forEach(ids::add);
				} else if (value instanceof List<?> list) {
					for (Object element : list) {
						if (element instanceof Record record) {
							for (var component : record.getClass().getRecordComponents()) {
								if (component.getName().equals("dialog")) {
									component.getAccessor().setAccessible(true);
									ids.add((String) component.getAccessor().invoke(record));
								}
							}
						}
					}
				}
			}
		}
		assertTrue(ids.size() >= 30, "found " + ids.size());
		for (LocalDate date = LocalDate.of(2026, 1, 1); date.getYear() == 2026; date = date.plusDays(1)) {
			ids.addAll(VillagerReactions.calendarDialogs(date));
		}
		for (String id : ids) {
			assertNotNull(library.get(id), "unknown dialog " + id);
		}
		assertTrue(VillagerReactions.calendarDialogs(LocalDate.of(2026, 12, 31)).containsAll(List.of("xljknt", "tkkegl")));
		assertTrue(library.conversations().stream().anyMatch(c -> c.getFirst().startsWith("gmrypk")));
	}

	@Test
	void greetsByTheNearestReputationBand() {
		assertEquals("clbjww", TradeReactions.greeting(0));
		assertEquals("clbjww", TradeReactions.greeting(20));
		assertEquals("kuhvdv", TradeReactions.greeting(60));
		assertEquals("vlrsrn", TradeReactions.greeting(400));
		assertEquals("xduuwm", TradeReactions.greeting(-500));
	}
}
