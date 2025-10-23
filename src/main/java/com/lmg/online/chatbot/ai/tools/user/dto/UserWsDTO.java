package com.lmg.online.chatbot.ai.tools.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWsDTO {

    @JsonProperty(required = true, value = "chat_message")
    private String chat_message;
    @JsonProperty(required = true)
    @JsonPropertyDescription("Full name of the customer")
    private String name;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Unique user identifier (email-like format)")
    private String uid;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's email address")
    private String email;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's first name")
    private String firstName;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's last name")
    private String lastName;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's gender (e.g., MALE, FEMALE, OTHER)")
    private String gender;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's registered mobile number")
    private String signInMobile;

    @JsonPropertyDescription("Customer's preferred currency details")
    private CurrencyDTO currency;

    @JsonPropertyDescription("Customer's preferred language details")
    private LanguageDTO language;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Customer's default address details")
    private AddressDTO defaultAddress;

    // --- Nested DTOs ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyDTO {
        @JsonPropertyDescription("Indicates if currency is active")
        private boolean active;

        @JsonPropertyDescription("Currency ISO code (e.g., INR)")
        private String isocode;

        @JsonPropertyDescription("Currency name or symbol (e.g., ₹)")
        private String name;

        @JsonPropertyDescription("Currency symbol (e.g., ₹)")
        private String symbol;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageDTO {
        @JsonPropertyDescription("Indicates if language is active")
        private boolean active;

        @JsonPropertyDescription("Language ISO code (e.g., en)")
        private String isocode;

        @JsonPropertyDescription("Language name (e.g., English)")
        private String name;

        @JsonPropertyDescription("Language native name (e.g., English)")
        private String nativeName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDTO {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Type of address (Home/Office)")
        private String addressType;

        @JsonPropertyDescription("Indicates if this is a billing address")
        private boolean billingAddress;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Customer’s contact number for this address")
        private String cellphone;

        @JsonPropertyDescription("Email associated with the address")
        private String email;

        @JsonPropertyDescription("First name associated with the address")
        private String firstName;

        @JsonPropertyDescription("Address line 1")
        private String line1;

        @JsonPropertyDescription("Address line 2")
        private String line2;

        @JsonPropertyDescription("Postal or ZIP code")
        private String postalCode;

        @JsonPropertyDescription("City or town name")
        private String town;

        @JsonPropertyDescription("Nearby landmark details")
        private String landmark;

        @JsonPropertyDescription("Formatted full address string")
        private String formattedAddress;

        @JsonPropertyDescription("Region or state information")
        private RegionDTO region;

        @JsonPropertyDescription("Country information")
        private CountryDTO country;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionDTO {
        @JsonPropertyDescription("Associated country ISO code (e.g., IN)")
        private String countryIso;

        @JsonPropertyDescription("Region ISO code (e.g., IN-KA)")
        private String isocode;

        @JsonPropertyDescription("Region or state name (e.g., Karnataka)")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryDTO {
        @JsonPropertyDescription("Country ISO code (e.g., IN)")
        private String isocode;

        @JsonPropertyDescription("Country name (e.g., India)")
        private String name;
    }
}
