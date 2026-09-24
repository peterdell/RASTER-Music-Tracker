package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/SAPFileTests.cpp. */
class SapFileTest {

	@Test
	void exportTypeBWithInitAndPlayer() {
		SapFile sap = new SapFile();
		sap.setAuthor(" AtariGuy ");
		sap.setName(" Cool Song ");
		sap.setDate("21/09/2026");
		sap.setType("B");
		sap.setSongs(2);
		sap.setDefaultSong(1);
		sap.setInitAddress(0x4000);
		sap.setPlayerAddress(0x4700);

		assertEquals(
				"SAP\r\n"
						+ "AUTHOR \" AtariGuy\"\r\n"
						+ "NAME \" Cool Song\"\r\n"
						+ "DATE \"21/09/2026\"\r\n"
						+ "TYPE B\r\n"
						+ "SONGS 2\r\n"
						+ "DEFSONG 1\r\n"
						+ "INIT 4000\r\n"
						+ "PLAYER 4700\r\n"
						+ "\r\n",
				sap.export());
	}

	@Test
	void exportTypeRIgnoresInitAndPlayer() {
		SapFile sap = new SapFile();
		sap.setAuthor("RCoder");
		sap.setName("RSong");
		sap.setDate("01/01/2000");
		sap.setType("R");
		sap.setStereo(true);
		// Type "R" never emits INIT/PLAYER, even though they're set here.
		sap.setInitAddress(0x9999);
		sap.setPlayerAddress(0x8888);

		assertEquals(
				"SAP\r\n"
						+ "AUTHOR \"RCoder\"\r\n"
						+ "NAME \"RSong\"\r\n"
						+ "DATE \"01/01/2000\"\r\n"
						+ "TYPE R\r\n"
						+ "STEREO\r\n"
						+ "\r\n",
				sap.export());
	}

	@Test
	void normalizeReplacesQuotesWithApostrophes() {
		SapFile sap = new SapFile();
		sap.setAuthor("Say \"Hi\"");
		sap.setName("N");
		sap.setDate("D");
		sap.setType("R");

		assertTrue(sap.export().contains("AUTHOR \"Say 'Hi'\"\r\n"));
	}

	@Test
	void gettersReturnWhatWasSet() {
		SapFile sap = new SapFile();
		sap.setAuthor("A");
		sap.setName("B");
		sap.setDate("C");
		sap.setSongs(5);
		sap.setDefaultSong(2);
		sap.setStereo(true);
		sap.setNTSC(true);
		sap.setType("B");
		sap.setInitAddress(0x1234);
		sap.setPlayerAddress(0x5678);

		assertEquals("A", sap.getAuthor());
		assertEquals("B", sap.getName());
		assertEquals("C", sap.getDate());
		assertEquals(5, sap.getSongs());
		assertEquals(2, sap.getDefaultSong());
		assertTrue(sap.isStereo());
		assertTrue(sap.isNTSC());
		assertEquals("B", sap.getType());
		assertEquals(0x1234, sap.getInitAddress());
		assertEquals(0x5678, sap.getPlayerAddress());
	}
}
