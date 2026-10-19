package com.goatteen.trading.watchlist;

import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class WatchlistControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean UserRepository users;
    @MockBean CurrentUserService currentUser;
    @MockBean WatchlistService watchlists;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void requiresAuthenticationForEveryOperation(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),
                        method.equals("GET") ? "/api/watchlist" : "/api/watchlist/10"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(watchlists);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void derivesEveryOwnerFromAuthenticationDespiteSuppliedOwnerFields() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(watchlists.list(7L)).thenReturn(List.of());
        mvc.perform(get("/api/watchlist").param("userId", "999")).andExpect(status().isOk());
        mvc.perform(put("/api/watchlist/10").param("userId", "999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/watchlist/10").param("userId", "999"))
                .andExpect(status().isNoContent());
        verify(watchlists).list(7L);
        verify(watchlists).add(7L, 10L);
        verify(watchlists).remove(7L, 10L);
        verifyNoMoreInteractions(watchlists);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void returnsHelpfulStatusCodesForUnavailableInstruments() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"))
                .when(watchlists).add(7L, 99L);
        doThrow(new IllegalArgumentException("This instrument is not currently tradable"))
                .when(watchlists).add(7L, 10L);
        mvc.perform(put("/api/watchlist/99")).andExpect(status().isNotFound());
        mvc.perform(put("/api/watchlist/10")).andExpect(status().isBadRequest());
    }
}
