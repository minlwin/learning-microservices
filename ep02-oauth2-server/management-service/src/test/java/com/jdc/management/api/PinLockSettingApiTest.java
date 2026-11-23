package com.jdc.management.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jdc.management.exceptions.handler.ExceptionHandlers;
import com.jdc.management.model.entity.PinLockSetting;
import com.jdc.management.model.repo.PinSettingRepo;
import com.jdc.management.props.ClientSystemProps;
import com.jdc.management.service.PinLockSettingService;
import com.jdc.management.event.service.PinLockSettingOutboxService;

class PinLockSettingApiTest {
    private PinSettingRepo repository;
    private MockMvc mvc;
    private static final String URL = "/api/setting/pin-locks";
    private static final String FORM = """
            {"tempLockFailsCount":3,"tempLockWaitMinutes":15,"permanentLockFailsCount":9}
            """;

    @BeforeEach
    void setup() {
        repository = mock(PinSettingRepo.class);
        var props = new ClientSystemProps();
        props.setSystems(List.of("Consumer", "Management"));
        var outboxService = mock(PinLockSettingOutboxService.class);
        var service = new PinLockSettingService(repository, props, outboxService);
        var builder = MockMvcBuilders.standaloneSetup(new PinLockSettingApi(service));
        builder.setControllerAdvice(new ExceptionHandlers());
        mvc = builder.build();
    }

    @Test
    void createsSettingsAndMapsCorrectedFieldNames() throws Exception {
        mvc.perform(post(URL + "/Consumer").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isOk());
        verify(repository).save(argThat(setting -> setting.getSystem().equals("Consumer")
                && setting.getTempLockFailsCount() == 3 && setting.getTempLockWailtMinutes() == 15
                && setting.getPermenantLockFailsCount() == 9 && !setting.isDeleted()));
    }

    @Test
    void listsSettingsAndOnlyUnconfiguredSystems() throws Exception {
        var entity = new PinLockSetting();
        entity.setSystem("Consumer");
        entity.setTempLockWailtMinutes(15);
        when(repository.findByDeletedFalseOrderBySystemAsc()).thenReturn(List.of(entity));
        mvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].system").value("Consumer"))
                .andExpect(jsonPath("$[0].tempLockWaitMinutes").value(15));
        mvc.perform(get(URL + "/systems")).andExpect(status().isOk())
                .andExpect(content().json("[\"Management\"]"));
    }

    @Test
    void updatesExistingSettings() throws Exception {
        var entity = new PinLockSetting();
        entity.setSystem("Consumer");
        when(repository.findById("Consumer")).thenReturn(Optional.of(entity));
        mvc.perform(put(URL + "/Consumer").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isOk());
        verify(repository).save(argThat(setting -> setting == entity && setting.getPermenantLockFailsCount() == 9));
    }

    @Test
    void rejectsDuplicateUnknownAndMissingSettings() throws Exception {
        var entity = new PinLockSetting();
        when(repository.findById("Consumer")).thenReturn(Optional.of(entity));
        mvc.perform(post(URL + "/Consumer").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(post(URL + "/Unknown").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isBadRequest());
        mvc.perform(put(URL + "/Management").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidValuesAndThresholds() throws Exception {
        mvc.perform(post(URL + "/Consumer").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL + "/Consumer").contentType(MediaType.APPLICATION_JSON)
                .content(FORM.replace("Count\":9", "Count\":2")))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }
}
