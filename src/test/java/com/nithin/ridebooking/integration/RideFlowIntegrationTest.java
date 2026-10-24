package com.nithin.ridebooking.integration;
import com.nithin.ridebooking.security.RateLimitFilter;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jayway.jsonpath.JsonPath;
import com.nithin.ridebooking.repository.DriverRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.nithin.ridebooking.repository.PaymentRepository;
import com.nithin.ridebooking.repository.RatingRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import static org.assertj.core.api.Assertions.assertThat;
import com.nithin.ridebooking.payment.PaymentGateway;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
@ActiveProfiles("test")
class RideFlowIntegrationTest {

    private static final String BOOKING = """
            {"pickupAddress":"Station Road","pickupLatitude":12.9716,"pickupLongitude":77.5946,
             "dropAddress":"City Mall","dropLatitude":12.9352,"dropLongitude":77.6245}
            """;

    @Autowired private WebApplicationContext context;
    @Autowired private RideRepository rideRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private PaymentGateway paymentGateway;
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Safety net: this test deletes all data, so it must never run on the real database
        assertThat(datasourceUrl)
                .as("Integration tests must only run against the in-memory test database")
                .startsWith("jdbc:h2:");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(rateLimitFilter)
                .apply(springSecurity())
                .build();
        ratingRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        rideRepository.deleteAllInBatch();
        driverRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
    
    // ---------- the tests ----------
    @Test
    void ratingFlow_bothSidesRateOnceAndTheSummaryUpdates() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        String driverToken = onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");
        Number driverId = JsonPath.read(booked, "$.driverId");

        // not allowed before the ride is completed
        doPost("/api/rides/" + rideId + "/rating", riderToken, "{\"stars\":5}")
                .andExpect(status().isBadRequest());

        doPut("/api/rides/" + rideId + "/start", driverToken, null).andExpect(status().isOk());
        doPut("/api/rides/" + rideId + "/complete", driverToken, null).andExpect(status().isOk());

        doPost("/api/rides/" + rideId + "/rating", riderToken, "{\"stars\":4,\"comment\":\"Nice and quick\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stars").value(4));
        doPost("/api/rides/" + rideId + "/rating", riderToken, "{\"stars\":5}")
                .andExpect(status().isBadRequest());     // already rated
        doPost("/api/rides/" + rideId + "/rating", driverToken, "{\"stars\":6}")
                .andExpect(status().isBadRequest());     // 6 stars is invalid
        doPost("/api/rides/" + rideId + "/rating", driverToken, "{\"stars\":5}")
                .andExpect(status().isCreated());        // the driver can rate the rider

        doGet("/api/drivers/" + driverId + "/rating", riderToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageStars").value(4.0))
                .andExpect(jsonPath("$.ratingCount").value(1));
        doGet("/api/drivers/" + driverId + "/reviews", riderToken)
                .andExpect(jsonPath("$.content[0].comment").value("Nice and quick"))
                .andExpect(jsonPath("$.content[0].raterFirstName").value("Test"));
    }
    @Test
    void fullRideFlow_bookStartComplete() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        String driverToken = onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AA0001"))
                .andExpect(jsonPath("$.fare").isNumber())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");

        doGet("/api/drivers/me", driverToken)
                .andExpect(jsonPath("$.available").value(false));

        doPut("/api/rides/" + rideId + "/start", driverToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        doPut("/api/rides/" + rideId + "/complete", driverToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());

        doGet("/api/drivers/me", driverToken)
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void secondRiderGetsTheNextNearestDriver() throws Exception {
        String rider1 = register("rider1@test.com", "RIDER");
        String rider2 = register("rider2@test.com", "RIDER");
        onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);
        onlineDriver("mid@test.com", "KA01AA0002", "DL-2", 12.9900, 77.6100);

        doPost("/api/rides", rider1, BOOKING)
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AA0001"));
        doPost("/api/rides", rider2, BOOKING)
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AA0002"));
    }

    @Test
    void noDriverNearbyLeavesTheRideRequested() throws Exception {
        String rider = register("rider@test.com", "RIDER");
        onlineDriver("far@test.com", "KA01AA0003", "DL-3", 13.1000, 77.7000);   // about 18 km away

        doPost("/api/rides", rider, BOOKING)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.driverId").isEmpty());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        doGet("/api/rides/1", null).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherRiderCannotSeeOrCancelARide() throws Exception {
        String owner = register("owner@test.com", "RIDER");
        String other = register("other@test.com", "RIDER");

        String booked = doPost("/api/rides", owner, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");

        doGet("/api/rides/" + rideId, other).andExpect(status().isForbidden());
        doPut("/api/rides/" + rideId + "/cancel", other, null).andExpect(status().isForbidden());
        doGet("/api/rides/" + rideId, owner).andExpect(status().isOk());
    }

    @Test
    void cannotRegisterAsAdmin() throws Exception {
        doPost("/api/auth/register", null, """
                {"name":"Evil","email":"evil@test.com","password":"Passw0rd!123","role":"ADMIN"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        register("rider@test.com", "RIDER");

        doPost("/api/auth/login", null, """
                {"email":"rider@test.com","password":"not-the-password"}""")
                .andExpect(status().isUnauthorized());
    }
    @Test
    void protectedEndpointsRejectRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rides/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ratings/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/rides/1/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stars\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejectedWith401NotAServerError() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void strangerCannotReadSomeoneElsesRide() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        String strangerToken = register("stranger@test.com", "RIDER");
        onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");

        doGet("/api/rides/" + rideId, strangerToken).andExpect(status().isForbidden());
        doGet("/api/rides/" + rideId, riderToken).andExpect(status().isOk());
    }

    @Test
    void adminRoleCannotBeSelfRegistered() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Evil Admin\",\"email\":\"evil@test.com\","
                                + "\"password\":\"Passw0rd123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Admin")));
    }

    @Test
    void loginIsBlockedAfterTooManyFailedAttempts() throws Exception {
        String body = "{\"email\":\"ghost@test.com\",\"password\":\"wrong-password-1\"}";

        // a separate address, so this test cannot lock out the other tests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(request -> { request.setRemoteAddr("10.9.9.9"); return request; }))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(request -> { request.setRemoteAddr("10.9.9.9"); return request; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
    @Test
    void rideHistoryRunsAFixedNumberOfQueriesNoMatterHowManyRides() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        String driverToken = onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        for (int i = 0; i < 5; i++) {
            // put the driver back at the pickup area before every booking
            doPut("/api/drivers/me/location", driverToken, "{\"latitude\":12.9720,\"longitude\":77.5950}")
                    .andExpect(status().isOk());

            String booked = doPost("/api/rides", riderToken, BOOKING)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Number rideId = JsonPath.read(booked, "$.id");

            var start = doPut("/api/rides/" + rideId + "/start", driverToken, null).andReturn().getResponse();
            assertThat(start.getStatus())
                    .as("ride " + (i + 1) + ": booking said " + booked + " / start said " + start.getContentAsString())
                    .isEqualTo(200);

            doPut("/api/rides/" + rideId + "/complete", driverToken, null).andExpect(status().isOk());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();   // otherwise the count below proves nothing
        statistics.clear();

        doGet("/api/rides/history?page=0&size=10", riderToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5));

        // user lookups + page + count. An N+1 bug would run 15 or more statements for 5 rides
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
    }

    @Test
    void ratingSummaryCacheIsClearedWhenANewRatingArrives() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        String driverToken = onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");
        Number driverId = JsonPath.read(booked, "$.driverId");

        // this read fills the cache with "no ratings yet"
        doGet("/api/drivers/" + driverId + "/rating", riderToken)
                .andExpect(jsonPath("$.ratingCount").value(0));

        doPut("/api/rides/" + rideId + "/start", driverToken, null).andExpect(status().isOk());
        doPut("/api/rides/" + rideId + "/complete", driverToken, null).andExpect(status().isOk());
        doPost("/api/rides/" + rideId + "/rating", riderToken, "{\"stars\":5}")
                .andExpect(status().isCreated());

        // without the eviction this would still say 0
        doGet("/api/drivers/" + driverId + "/rating", riderToken)
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.averageStars").value(5.0));
    }

    @Test
    void riderCannotHaveTwoActiveRides() throws Exception {
        String rider = register("rider@test.com", "RIDER");

        doPost("/api/rides", rider, BOOKING).andExpect(status().isCreated());
        doPost("/api/rides", rider, BOOKING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already have an active ride"));
    }

    @Test
    void estimateReturnsABreakdown() throws Exception {
        String rider = register("rider@test.com", "RIDER");

        doPost("/api/rides/estimate", rider, """
                {"pickupLatitude":12.9716,"pickupLongitude":77.5946,
                 "dropLatitude":12.9352,"dropLongitude":77.6245}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(14))
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    void estimateOfTheSamePointIsTheMinimumFare() throws Exception {
        String rider = register("rider@test.com", "RIDER");

        doPost("/api/rides/estimate", rider, """
                {"pickupLatitude":12.9716,"pickupLongitude":77.5946,
                 "dropLatitude":12.9716,"dropLongitude":77.5946}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(0))
                .andExpect(jsonPath("$.total").value(80.0));
    }
    @Test
    void fullJourney_bookCompletePayGetReceiptAndRate() throws Exception {
        when(paymentGateway.createIntent(anyLong(), anyString(), anyLong()))
                .thenReturn(new PaymentGateway.IntentResult("pi_test_1", "secret_test_1"));
        when(paymentGateway.parseWebhook(anyString(), anyString()))
                .thenReturn(new PaymentGateway.WebhookEvent("payment_intent.succeeded", "pi_test_1"));

        String riderToken = register("rider@test.com", "RIDER");
        String strangerToken = register("stranger@test.com", "RIDER");
        String driverToken = onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);

        // book
        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");

        // privacy: a stranger cannot see this ride
        doGet("/api/rides/" + rideId, strangerToken).andExpect(status().isForbidden());

        // not allowed yet: paying or receipt before the ride is completed
        bearerPost("/api/rides/" + rideId + "/payment", riderToken).andExpect(status().isBadRequest());

        // ride lifecycle
        doPut("/api/rides/" + rideId + "/start", driverToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        doPut("/api/rides/" + rideId + "/complete", driverToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // a finished ride cannot be cancelled
        doPut("/api/rides/" + rideId + "/cancel", riderToken, null)
                .andExpect(status().is4xxClientError());

        // payment: create, Stripe calls the webhook, status becomes SUCCEEDED
        bearerPost("/api/rides/" + rideId + "/payment", riderToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", "t=1,v1=fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        bearerGet("/api/rides/" + rideId + "/payment", riderToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        bearerPost("/api/rides/" + rideId + "/payment", riderToken)
                .andExpect(status().isBadRequest());          // already paid

        // receipt: JSON and a real PDF
        bearerGet("/api/rides/" + rideId + "/receipt", riderToken).andExpect(status().isOk());
        byte[] pdf = bearerGet("/api/rides/" + rideId + "/receipt/pdf", riderToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // both sides rate
        doPost("/api/rides/" + rideId + "/rating", riderToken, "{\"stars\":5,\"comment\":\"Great\"}")
                .andExpect(status().isCreated());
        doPost("/api/rides/" + rideId + "/rating", driverToken, "{\"stars\":4}")
                .andExpect(status().isCreated());

        // history shows the finished ride
        doGet("/api/rides/history?page=0&size=10", riderToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }
    @Test
    void twoRidersRacingForOneDriverCannotBothGetThem() throws Exception {
        String riderA = register("racea@test.com", "RIDER");
        String riderB = register("raceb@test.com", "RIDER");
        onlineDriver("only@test.com", "KA01AA0009", "DL-9", 12.9720, 77.5950);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (String token : List.of(riderA, riderB)) {
            results.add(pool.submit(() -> {
                go.await();                                   // both threads start together
                return doPost("/api/rides", token, BOOKING)
                        .andReturn().getResponse().getContentAsString();
            }));
        }
        go.countDown();

        int rideWithDriver = 0;
        for (Future<String> result : results) {
            String body = result.get(20, TimeUnit.SECONDS);
            try {
                Object driverId = JsonPath.read(body, "$.driverId");
                if (driverId != null) {
                    rideWithDriver++;
                }
            } catch (Exception noDriverInResponse) {
                // an error body or a ride without a driver: not an assignment
            }
        }
        pool.shutdown();

        assertThat(rideWithDriver).isEqualTo(1);
    }
    @Test
    void wrongPeopleCannotDriveTheRideLifecycle() throws Exception {
        String riderToken = register("rider@test.com", "RIDER");
        onlineDriver("near@test.com", "KA01AA0001", "DL-1", 12.9720, 77.5950);
        String otherDriverToken = onlineDriver("other@test.com", "KA01AA0002", "DL-2", 13.5, 78.5);

        String booked = doPost("/api/rides", riderToken, BOOKING)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number rideId = JsonPath.read(booked, "$.id");

        doPut("/api/rides/" + rideId + "/start", riderToken, null)
                .andExpect(status().is4xxClientError());       // a rider cannot start
        doPut("/api/rides/" + rideId + "/start", otherDriverToken, null)
                .andExpect(status().is4xxClientError());       // another driver cannot start
    }

    // ---------- helpers ----------

    private String register(String email, String role) throws Exception {
        String json = doPost("/api/auth/register", null, """
                {"name":"Test %s","email":"%s","password":"Passw0rd!123","phone":"9999999999","role":"%s"}"""
                .formatted(role, email, role))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private String onlineDriver(String email, String vehicleNumber, String license,
                                double lat, double lng) throws Exception {
        String token = register(email, "DRIVER");

        doPost("/api/drivers/profile", token, """
                {"vehicleModel":"Swift","vehicleNumber":"%s","licenseNumber":"%s"}"""
                .formatted(vehicleNumber, license)).andExpect(status().isCreated());
        doPut("/api/drivers/me/location", token, """
                {"latitude":%s,"longitude":%s}""".formatted(lat, lng)).andExpect(status().isOk());
        doPut("/api/drivers/me/availability", token, "{\"available\":true}")
                .andExpect(status().isOk());
        return token;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }
    private ResultActions bearerGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private ResultActions bearerPost(String url, String token) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", "Bearer " + token));
    }
    private ResultActions doGet(String url, String token) throws Exception {
        return send(get(url), token, null);
    }

    private ResultActions doPost(String url, String token, String body) throws Exception {
        return send(post(url), token, body);
    }

    private ResultActions doPut(String url, String token, String body) throws Exception {
        return send(put(url), token, body);
    }
}