package com.fleurdecoquillage.app.bookings.app;

import com.fleurdecoquillage.app.bookings.domain.CareBooking;
import com.fleurdecoquillage.app.bookings.repo.CareBookingRepository;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingRequest;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingResponse;
import com.fleurdecoquillage.app.bookings.web.mapper.CareBookingMapper;
import com.fleurdecoquillage.app.care.repo.CareRepository;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareBookingService {
    private final CareBookingRepository repo;
    private final UserRepository userRepository;
    private final CareRepository careRepository;

    public CareBookingService(CareBookingRepository repo, UserRepository userRepository, CareRepository careRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
    }

    @Transactional(readOnly = true)
    public Page<CareBookingResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CareBookingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CareBookingResponse get(Long id) {
        return repo.findById(id).map(CareBookingMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Care booking not found: " + id));
    }

    @Transactional
    public CareBookingResponse create(CareBookingRequest req) {
        var user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.userId()));
        var care = careRepository.findById(req.careId())
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + req.careId()));

        CareBooking b = new CareBooking();
        b.setUser(user);
        b.setCare(care);
        CareBookingMapper.updateEntity(b, req);
        return CareBookingMapper.toResponse(repo.save(b));
    }

    @Transactional
    public CareBookingResponse update(Long id, CareBookingRequest req) {
        CareBooking b = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Care booking not found: " + id));
        // Authoritative user/care can stay unchanged; update quantity/status only
        CareBookingMapper.updateEntity(b, req);
        return CareBookingMapper.toResponse(repo.save(b));
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }
}

