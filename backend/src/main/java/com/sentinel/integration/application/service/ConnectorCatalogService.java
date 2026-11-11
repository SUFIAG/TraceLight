package com.sentinel.integration.application.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The connector catalogue.
 *
 * <p>Integration time is the single biggest reason a fraud platform takes
 * twelve months to deliver value, and the second biggest is that nobody knows
 * which fields the core banking system actually exposes. Publishing the
 * mapping each connector expects turns that discovery phase into a
 * configuration step.
 */
@Service
public class ConnectorCatalogService {

    public List<Map<String, Object>> catalogue() {
        return List.of(
                connector("TEMENOS_T24", "Temenos T24", "CORE_BANKING",
                        List.of("ACCOUNT", "TRANSACTION", "CUSTOMER"), "REST + MQ"),
                connector("FINACLE", "Infosys Finacle", "CORE_BANKING",
                        List.of("ACCOUNT", "TRANSACTION", "CUSTOMER"), "REST"),
                connector("FIS_PROFILE", "FIS Profile", "CORE_BANKING",
                        List.of("ACCOUNT", "TRANSACTION"), "REST"),
                connector("FISERV_DNA", "Fiserv DNA", "CORE_BANKING",
                        List.of("ACCOUNT", "TRANSACTION"), "REST"),
                connector("MAMBU", "Mambu", "CORE_BANKING",
                        List.of("ACCOUNT", "TRANSACTION", "CUSTOMER"), "REST + webhooks"),
                connector("THOUGHT_MACHINE", "Thought Machine Vault", "CORE_BANKING",
                        List.of("ACCOUNT", "POSTING", "CUSTOMER"), "Streaming + REST"),
                connector("MARQETA", "Marqeta", "CARD_PROCESSOR",
                        List.of("AUTHORISATION", "CARD", "CARDHOLDER"), "Webhooks"),
                connector("STRIPE", "Stripe", "PSP",
                        List.of("CHARGE", "DISPUTE", "CUSTOMER"), "Webhooks"),
                connector("ADYEN", "Adyen", "PSP",
                        List.of("PAYMENT", "DISPUTE"), "Webhooks"),
                connector("PLAID", "Plaid", "OPEN_BANKING",
                        List.of("ACCOUNT", "TRANSACTION", "IDENTITY"), "REST"),
                connector("SWIFT_MX", "SWIFT MX / ISO 20022", "PAYMENT_RAIL",
                        List.of("PACS_008", "PACS_004", "CAMT_053"), "MQ"),
                connector("FEDNOW", "FedNow", "PAYMENT_RAIL",
                        List.of("CREDIT_TRANSFER", "RFP"), "ISO 20022"),
                connector("SEPA_INSTANT", "SEPA Instant", "PAYMENT_RAIL",
                        List.of("SCT_INST"), "ISO 20022"),
                connector("UPI", "UPI", "PAYMENT_RAIL",
                        List.of("COLLECT", "PAY"), "REST"),
                connector("PIX", "Pix", "PAYMENT_RAIL",
                        List.of("PIX_TRANSFER"), "REST"),
                connector("SNOWFLAKE", "Snowflake", "DATA_WAREHOUSE",
                        List.of("DECISION_EXPORT", "CASE_EXPORT"), "Bulk export"),
                connector("DATABRICKS", "Databricks", "DATA_WAREHOUSE",
                        List.of("DECISION_EXPORT", "FEATURE_EXPORT"), "Bulk export"),
                connector("BIGQUERY", "BigQuery", "DATA_WAREHOUSE",
                        List.of("DECISION_EXPORT"), "Bulk export")
        );
    }

    /** Export targets the platform can push decision and case data to. */
    public List<Map<String, Object>> exportTargets() {
        return List.of(
                Map.of("target", "SNOWFLAKE", "formats", List.of("PARQUET", "CSV"), "mode", "INCREMENTAL"),
                Map.of("target", "DATABRICKS", "formats", List.of("DELTA", "PARQUET"), "mode", "INCREMENTAL"),
                Map.of("target", "BIGQUERY", "formats", List.of("AVRO", "CSV"), "mode", "INCREMENTAL"),
                Map.of("target", "S3", "formats", List.of("PARQUET", "JSONL"), "mode", "FULL_OR_INCREMENTAL"));
    }

    private Map<String, Object> connector(String key, String name, String category,
                                          List<String> entities, String transport) {
        return Map.of(
                "key", key,
                "name", name,
                "category", category,
                "entities", entities,
                "transport", transport,
                "status", "AVAILABLE");
    }
}
