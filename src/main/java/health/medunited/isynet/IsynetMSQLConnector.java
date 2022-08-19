package health.medunited.isynet;

import health.medunited.artemis.PrescriptionConsumer;
import health.medunited.service.MedicationDbLookup;
import health.medunited.model.*;

import javax.enterprise.context.ApplicationScoped;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class IsynetMSQLConnector {

    private static final Logger log = Logger.getLogger(PrescriptionConsumer.class.getName());

    public void insertToIsynet(BundleStructure bundleStructure, int IDValue) {

        String[] dosage = bundleStructure.getMedicationStatement().getDosage().split("-");
        String morgens = dosage[0];
        String mittags = dosage[1];
        String abends = dosage[2];
        String nachts = dosage[3];

        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS");
        DateTimeFormatter dtf2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        LocalDateTime now = LocalDateTime.now();
        String timestamp1 = dtf1.format(now).replace(" ","T");
        String timestamp2 = dtf2.format(now).replace(" ","T");

        String PZNtoLookup = bundleStructure.getMedicationStatement().getPZN();
        List<String> tableEntry = MedicationDbLookup.lookupMedicationByPZN(PZNtoLookup);
        if (tableEntry != null) {
            String medicationName = MedicationDbLookup.getMedicationName(tableEntry);
            String quantity = MedicationDbLookup.getQuantity(tableEntry);
            String norm = MedicationDbLookup.getNorm(tableEntry);
            String AVP = MedicationDbLookup.getAVP(tableEntry);
            String ATC = MedicationDbLookup.getATC(tableEntry);
            String composition = MedicationDbLookup.getComposition(tableEntry);
            log.info("[ MEDICATION OBTAINED FROM DB ] PZN: " + PZNtoLookup + " // name: " + medicationName + " // quantity: " + quantity + " // norm: " + norm + " // AVP: " + AVP + " // ATC: " + ATC + " // composition: " + composition);
        }

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        String connectionUrl = "jdbc:sqlserver://lhtufukeqw3tayq1.myfritz.net:1433;databaseName=WINACS;user=AP31;password=722033800;trustServerCertificate=true";
        try (Connection con = DriverManager.getConnection(connectionUrl); Statement stmt = con.createStatement()) {

            String SQL_get_patient_nummer = "" +
//                    SET XACT_ABORT ON: in case of an error, rollback will be issued automatically
                    "SET XACT_ABORT ON\n" +
                    "begin transaction\n" +
                    "SELECT Nummer FROM Patient WHERE (Vorname = '" + bundleStructure.getPatient().getFirstName() + "' AND Name = '" + bundleStructure.getPatient().getLastName() + "' AND Geburtsdatum='"+ bundleStructure.getPatient().getBirthDate() + " 00:00:00.000"+"');" +
                    "commit transaction";

//            QUERY EXECUTED: Get PatientNummer
            ResultSet rs = stmt.executeQuery(SQL_get_patient_nummer);
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnsNumber = rsmd.getColumnCount();
            String patientNummer = "";
            while (rs.next()) {
                log.info("SQL_get_patient_nummer:");
                for (int i = 1; i <= columnsNumber; i++) {
                    if (i > 1) log.info(",  ");
                    patientNummer = rs.getString(i);
                    log.info(patientNummer + " (" + rsmd.getColumnName(i) + ")");
                }
            }

            String SQL_delete_VerordnungsmodulMedikamentDbo = "DELETE FROM VerordnungsmodulMedikamentDbo WHERE Id > 0";
            String SQL_delete_VerordnungsmodulRezepturWirkstoffDbo = "DELETE FROM VerordnungsmodulRezepturWirkstoffDbo WHERE Id > 0";
            String SQL_delete_VerordnungsmodulMedikationDbo = "DELETE FROM VerordnungsmodulMedikationDbo WHERE Id > 0";
            String SQL_delete_VerordnungsmodulRezeptDbo = "DELETE FROM VerordnungsmodulRezeptDbo WHERE Id > 0";
            String SQL_delete_VerordnungsmodulDosierungDbo = "DELETE FROM VerordnungsmodulDosierungDbo WHERE Id > 0";
            String SQL_delete_ScheinMed = "DELETE FROM ScheinMed WHERE Nummer > 0";
            String SQL_delete_KrablLink = "DELETE FROM KrablLink WHERE Nummer > 0";
            String SQL_delete_KrablLinkID = "DELETE FROM KrablLinkID WHERE Nummer > 0";
            String SQL_delete_ScheinMedDaten = "DELETE FROM ScheinMedDaten WHERE Nummer > 0";

//            QUERIES EXECUTED: Delete entries in tables
            if (IDValue == 1) {
                stmt.execute(SQL_delete_ScheinMedDaten);
                stmt.execute(SQL_delete_KrablLinkID);
                stmt.execute(SQL_delete_KrablLink);
                stmt.execute(SQL_delete_ScheinMed);
                stmt.execute(SQL_delete_VerordnungsmodulDosierungDbo);
                stmt.execute(SQL_delete_VerordnungsmodulMedikationDbo);
                stmt.execute(SQL_delete_VerordnungsmodulRezeptDbo);
                stmt.execute(SQL_delete_VerordnungsmodulRezepturWirkstoffDbo);
                stmt.execute(SQL_delete_VerordnungsmodulMedikamentDbo);
            }

            String IDvalue = String.valueOf(IDValue);
            log.info("ID VALUE IS " + IDvalue);
            assert tableEntry != null;
            String SQL_insert_medication = "" +
//                    SET XACT_ABORT ON: in case of an error, rollback will be issued automatically
                    "SET XACT_ABORT ON\n" +
                    "begin transaction\n" +
//                    Insert into VerordnungsmodulMedikamentDbo table (Prescription module medication table)
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulMedikamentDbo] ON\n" +
                    "INSERT [dbo].[VerordnungsmodulMedikamentDbo] ([Id], [Pzn], [HandelsnameOderFreitext], [Hersteller], [AtcCodes], [AtcCodeBedeutungen], [Darreichungsform], [DarreichungsformAsFreitext], [PackungsgroesseText], [PackungsgroesseWert], [PackungsgroesseEinheit], [PackungsgroesseEinheitCode], [Normgroesse], [Preis_IsSet], [Preis_ApothekenVerkaufspreisCent], [Preis_FestbetragCent], [Preis_MehrkostenCent], [Preis_ZuzahlungCent], [Preis_GesamtzuzahlungCent], [Typ], [Farbe], [IsPriscus], [Created], [DatasetCreated], [UserCreated], [LastChanged], [DatasetLastChanged], [UserLastChanged], [IsArchiviert], [Hilfsmittelpositionsnummer])" +
                    "VALUES (" + IDvalue + ", " + bundleStructure.getMedicationStatement().getPZN() + ", N'" + MedicationDbLookup.getMedicationName(tableEntry) + "', N'', N'" + MedicationDbLookup.getATC(tableEntry) + "', N'" + MedicationDbLookup.getComposition(tableEntry) + "', N'', N'', N'', N'', N'', N'', 1, 1, 1665, NULL, NULL, 500, 500, 1, NULL, 0, CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', 0, NULL)\n" +
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulMedikamentDbo] OFF\n" +
//                    Insert into VerordnungsmodulRezepturWirkstoffDbo table (Prescription module active ingredient table)
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulRezepturWirkstoffDbo] ON\n" +
                    "INSERT [dbo].[VerordnungsmodulRezepturWirkstoffDbo] ([Id], [AtcCode], [AtcCodeBedeutung], [Freitext], [WirkstaerkeWert], [WirkstaerkeEinheit], [WirkstaerkeEinheitCode], [ProduktmengeWert], [ProduktmengeEinheit], [ProduktmengeEinheitCode], [MedikamentDbo_Id])" +
                    "VALUES ("+ IDvalue +", NULL, NULL, N'" + MedicationDbLookup.getComposition(tableEntry) + "', N'599', N'Milligramm', N'mg', CAST(1.00 AS Decimal(18, 2)), N'AuTro', N'1', "+ IDvalue +")\n" +
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulRezepturWirkstoffDbo] OFF\n" +
//                    Insert into VerordnungsmodulRezeptDbo table (Prescription module recipe table)
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulRezeptDbo] ON\n" +
                    "INSERT [dbo].[VerordnungsmodulRezeptDbo] ([Id], [RezeptGruppierung], [OnRezeptWeight], [MedikamentId], [PatientId], [Ausstellungsdatum], [Erstellungsdatum], [BehandlerId], [KostentraegerId], [BetriebsstaetteId], [AnzahlWert], [AnzahlEinheit], [AnzahlEinheitCode], [RezeptZusatzinfos_IsSet], [RezeptZusatzinfos_Gebuehrenfrei], [RezeptZusatzinfos_Unfall], [RezeptZusatzinfos_Arbeitsunfall], [RezeptZusatzinfos_Noctu], [RezeptTyp], [KennzeichenStatus], [MPKennzeichen], [AutIdem], [RezeptZeile], [VerordnungsStatus], [BtmSonderkennzeichen], [TRezeptZusatzinfos_IsSet], [TRezeptZusatzinfos_SicherheitsbestimmungenEingehalten], [TRezeptZusatzinfos_InformationfsmaterialAusgegeben], [TRezeptZusatzinfos_InOffLabel], [HilfsmittelRezeptZusatzinfos_IsSet], [HilfsmittelRezeptZusatzinfos_ProduktnummerPrintType], [HilfsmittelRezeptZusatzinfos_DiagnoseText], [HilfsmittelRezeptZusatzinfos_Zeitraum], [AdditionalText], [Annotation], [ReasonForTreatment], [HasToApplyAdditionalTextToRezept], [HasToApplyAnnotationTextToRezept], [IsHilfsmittelRezept], [IsImpfstoffRezept], [VertragsZusatzinfos_ZusatzhinweisHzvGruen], [VertragsZusatzinfos_BvgKennzeichen], [VertragsZusatzinfos_Begruendungspflicht], [VertragsZusatzinfos_StellvertreterMitgliedsNr], [VertragsZusatzinfos_StellvertreterMediId], [VertragsZusatzinfos_StellvetreterLanr], [Created], [DatasetCreated], [UserCreated], [LastChanged], [DatasetLastChanged], [UserLastChanged], [IsArchiviert], [AsvTeamNummer], [StempelId], [DosierungsPflichtAuswahl], [IsKuenstlicheBefruchtung], [VertragsZusatzinfos_IsSet], [VertragsZusatzinfos_Wirkstoffzeile], [VertragsZusatzinfos_IsWirkstoffzeileActivated], [IsErezept], [AbgabehinweisApotheke])" +
                    "VALUES (" + IDvalue + ", N'', 0, " + IDvalue + ", N'" + patientNummer + "', CAST(N'" + timestamp1 + "' AS DateTime2), CAST(N'" + timestamp1 + "' AS DateTime2), N'BEH-1', N'2', N'1', N'1', N'Pckg', N'1', 1, 0, 0, 0, 0, 0, 0, 0, 1, N'" + MedicationDbLookup.getMedicationName(tableEntry) + "\n" +
                    "PZN" + bundleStructure.getMedicationStatement().getPZN() + " »" + bundleStructure.getMedicationStatement().getDosage() + "«'" + ", 1, NULL, 0, 0, 0, 0, 0, 0, NULL, 0, NULL, NULL, NULL, 1, 1, 0, 0, NULL, 0, 0, NULL, NULL, NULL, CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', 0, NULL, N'101', 3, 0, 0, NULL, 0, 1, NULL)\n" +
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulRezeptDbo] OFF\n" +
//                    Insert into VerordnungsmodulMedikationDbo table (Prescription module medication table)
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulMedikationDbo] ON\n" +
                    "INSERT [dbo].[VerordnungsmodulMedikationDbo] ([Id], [RezeptId], [MedikamentId], [PatientId], [DatumVerordnet], [IsDauermedikation], [MpKennzeichen], [DatumAbgesetzt], [GrundAbgesetzt], [Created], [DatasetCreated], [UserCreated], [LastChanged], [DatasetLastChanged], [UserLastChanged], [IsArchiviert])" +
                    "VALUES (" + IDvalue + "," + IDvalue + ", " + IDvalue + ", N'" + patientNummer + "', CAST(N'" + timestamp1 + "' AS DateTime2), 0, 0, NULL, NULL, CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', CAST(N'" + timestamp1 + "+02:00' AS DateTimeOffset), N'1', N'ANW-1', 0)\n" +
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulMedikationDbo] OFF\n" +
//                    Insert into ScheinMed table
                    "SET IDENTITY_INSERT [dbo].[ScheinMed] ON\n" +
                    "INSERT [dbo].[ScheinMed] ([Nummer], [ScheinNummer], [PatientNummer], [Suchwort], [PZN], [Verordnungstyp], [Betragsspeicher], [AVP], [Festbetrag], [Bruttobetrag], [Nettobetrag], [Diagnose], [ICD], [AutIdem], [Grenzpreis], [MandantGeändert], [UserGeändert], [DatumÄnderung], [UnteresPreisdrittel], [KrablLinkNrRezept], [BTMGebühr], [DDDPackung], [Übertragen], [FarbKategorie], [Merkmal], [KrablLinkNr])" +
                    "VALUES (" + IDvalue + ", 1," + patientNummer + ", N'" + bundleStructure.getMedicationStatement().getPZN() + "', N'" + bundleStructure.getMedicationStatement().getPZN() + "', N'LM', 100, " + MedicationDbLookup.getAVP(tableEntry) + ", 0.0000, " + MedicationDbLookup.getAVP(tableEntry) + ", 11.6500, N'', N'', 1, 0.0000, 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 0, 0, 0.0000, 0, 0, N'', N''," + IDvalue + ")\n" +
                    "SET IDENTITY_INSERT [dbo].[ScheinMed] OFF\n" +
//                    Insert into KrablLink table
                    "SET IDENTITY_INSERT [dbo].[KrablLink] ON\n" +
                    "INSERT [dbo].[KrablLink] ([Nummer], [PatientNummer], [Satzart], [Datum], [Kategorie], [Kurzinfo], [Passwort], [MandantGeändert], [UserGeändert], [DatumÄnderung], [ScheinNummer], [GruppenNummer], [Hintergrundfarbe], [Detail], [MandantAnlage], [UserAnlage], [DatumAnlage], [MandantFremd], [FreigabeStatus], [VersandStatus], [Uhrzeitanlage])" +
                    "VALUES (" + IDvalue + "," + patientNummer + ", 4000, CAST(N'" + timestamp2 + "' AS DateTime), N'LM', N', Dos.: " + bundleStructure.getMedicationStatement().getDosage() + ", PZN: " + bundleStructure.getMedicationStatement().getPZN() + ", AVP: " + MedicationDbLookup.getAVP(tableEntry) + "', 0, 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 0, 0, 0, N'\n" +
                    "', 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 0, 0, 0, CAST(N'1899-12-30T15:04:31.000' AS DateTime))\n" +
                    "SET IDENTITY_INSERT [dbo].[KrablLink] OFF\n" +
//                    Insert into KrablLinkID table
                    "SET IDENTITY_INSERT [dbo].[KrablLinkID] ON\n" +
                    "INSERT [dbo].[KrablLinkID] ([Nummer], [PatientNummer], [KrablLinkNummer], [IDType], [ID], [MandantAnlage], [UserAnlage], [DatumAnlage], [MandantGeändert], [UserGeändert], [DatumÄnderung], [Fremdsystem], [Erzeugersystem], [Status], [Bemerkung])" +
                    "VALUES (" + IDvalue + ", " + patientNummer + "," + IDvalue + ", 7, " + IDvalue + ", 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 1, 1, 0, N'f4835bad-c18b-4653-b706-89b6f5b06772')\n" +
                    "SET IDENTITY_INSERT [dbo].[KrablLinkID] OFF\n" +
//                    Insert into ScheinMedDaten
                    "SET IDENTITY_INSERT [dbo].[ScheinMedDaten] ON\n" +
                    "INSERT [dbo].[ScheinMedDaten] ([Nummer], [Suchwort], [Klasse], [Typ], [Langtext], [Packungsart], [NNummer], [Darreichungsform], [Packungsgröße], [PZNummer], [StandardDosierung], [Betrag], [Festbetrag], [Grenzpreis], [Anatomieklasse], [Hersteller], [Wirkstoff], [Generika], [NurPrivatrezept], [BTMPräparat], [Bevorzugt], [Geschützt], [AußerHandel], [Negativliste], [Rückruf], [Datenanbieter], [MandantAnlage], [UserAnlage], [DatumAnlage], [MandantGeändert], [UserGeändert], [DatumÄnderung], [UnteresPreisdrittel], [OTC], [Zuzahlungsbefreit], [WirkstoffMenge], [WirkstoffMengenEinheit], [LetzterPreis], [PreisÄnderung], [LifeStyle], [ApothekenPflicht], [VerschreibungsPflicht], [Reimport], [AlternativeVorhanden], [ZweitMeinung], [PNH], [PNHBezeichnung], [DDDKosten], [OTX], [TRezept], [KombiPraeparat], [AutIdemKennung], [PriscusListe], [NeueinfuehrungsDatum], [HerstellerID], [ErstattungsBetrag], [DokuPflichtTransfusion], [VOEinschraenkungAnlage3], [Therapiehinweis], [MedizinProdukt], [MPVerordnungsfaehig], [MPVOBefristung], [Verordnet], [MitReimport], [WSTZeile], [WSTNummer], [IMMVorhanden], [Sortierung], [ATCLangtext], [ErstattungStattAbschlag], [VertragspreisNach129_5], [NormGesamtzuzahlung], [Verordnungseinschraenkung], [Verordnungsausschluss], [VOAusschlussAnlage3])" +
                    "VALUES ("+ IDvalue +", N'" + bundleStructure.getMedicationStatement().getPZN() + "', N'', 1, N'" + MedicationDbLookup.getMedicationName(tableEntry) + "', 1, 1, 0, N'5', N'" + bundleStructure.getMedicationStatement().getPZN() + "', N'', " + MedicationDbLookup.getAVP(tableEntry) + ", 0.0000, 0.0000, N'S01AE01', N'Pharma Gerke Arzneimittelvertriebs GmbH', N'" + MedicationDbLookup.getComposition(tableEntry) + "', 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 1, 1, CAST(N'" + timestamp2 + "' AS DateTime), 0, 0, 0, 3, N'mg', 0.0000, N'', 0, 0, 0, 0, 0, 0, N'', N'', 0.0000, 0, 0, 0, 0, 0, CAST(N'1899-12-30T00:00:00.000' AS DateTime), 0, 0.0000, 0, 0, 0, 0, 0, CAST(N'1899-12-30T00:00:00.000' AS DateTime), 0, 0, N'', N'', 0, N'', N'" + MedicationDbLookup.getComposition(tableEntry) + "', 0, 0, 0.0000, 0, 0, 0)\n" +
                    "SET IDENTITY_INSERT [dbo].[ScheinMedDaten] OFF\n" +
//                    Insert into VerordnungsmodulDosierungDbo table
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulDosierungDbo] ON\n" +
                    "INSERT [dbo].[VerordnungsmodulDosierungDbo] ([ID], [Morgens], [Mittags], [Abends], [Nachts], [DosierungsFreitext], [StartOfTaking], [EndOfTaking], [Status], [DosierEinheit], [DosierEinheitCode], [RezeptDbo_Id], [MedikationDbo_Id])" +
                    "VALUES (" + IDvalue + "," + morgens + "," + mittags + "," + abends + "," + nachts + ", NULL, NULL, NULL, 1, 1, 1," + IDvalue + "," + IDvalue + ")\n" +
                    "SET IDENTITY_INSERT [dbo].[VerordnungsmodulDosierungDbo] OFF\n" +
                    "commit transaction";

//            QUERY EXECUTED: Insert a prescription into isynet
            stmt.execute(SQL_insert_medication);

            String SQL_VerordnungsmodulMedikamentDbo_table = "SELECT * FROM VerordnungsmodulMedikamentDbo\n";
//            QUERY EXECUTED: Get content of one of the tables related to the prescription to check if it was added
            ResultSet rs2 = stmt.executeQuery(SQL_VerordnungsmodulMedikamentDbo_table);
            ResultSetMetaData rsmd2 = rs2.getMetaData();
            int columnsNumber2 = rsmd2.getColumnCount();

//            For printing the content of table VerordnungsmodulMedikamentDbo
            while (rs2.next()) {
                log.info("SQL_VerordnungsmodulMedikamentDbo_table:");
                StringBuilder result = new StringBuilder();
                for (int i = 1; i <= columnsNumber2; i++) {
                    if (i > 1) result.append(",  ");
                    result.append(rs2.getString(i));
                    result.append(" (").append(rsmd2.getColumnName(i)).append(")");
                }
                log.info(result.toString());
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
