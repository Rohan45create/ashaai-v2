package com.ashaai.backend.service;

import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final AshaRepository ashaRepository;
    private final HouseholdRepository householdRepository;
    private final PregnancyRepository pregnancyRepository;
    private final ChildRepository childRepository;
    private final VaccinationRepository vaccinationRepository;
    private final ReferralRepository referralRepository;
    
    private final HouseholdMemberRepository householdMemberRepository;
    private final BirthRecordRepository birthRecordRepository;
    private final DiseaseCaseRepository diseaseCaseRepository;
    private final NcdRecordRepository ncdRecordRepository;
    private final DeathRecordRepository deathRecordRepository;
    private final FamilyPlanningRepository familyPlanningRepository;
    private final VisitRepository visitRepository;
    private final AshaHeadRepository ashaHeadRepository;

    public AdminService(
            AshaRepository ashaRepository,
            HouseholdRepository householdRepository,
            PregnancyRepository pregnancyRepository,
            ChildRepository childRepository,
            VaccinationRepository vaccinationRepository,
            ReferralRepository referralRepository,
            HouseholdMemberRepository householdMemberRepository,
            BirthRecordRepository birthRecordRepository,
            DiseaseCaseRepository diseaseCaseRepository,
            NcdRecordRepository ncdRecordRepository,
            DeathRecordRepository deathRecordRepository,
            FamilyPlanningRepository familyPlanningRepository,
            VisitRepository visitRepository,
            AshaHeadRepository ashaHeadRepository
    ) {
        this.ashaRepository = ashaRepository;
        this.householdRepository = householdRepository;
        this.pregnancyRepository = pregnancyRepository;
        this.childRepository = childRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.referralRepository = referralRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.birthRecordRepository = birthRecordRepository;
        this.diseaseCaseRepository = diseaseCaseRepository;
        this.ncdRecordRepository = ncdRecordRepository;
        this.deathRecordRepository = deathRecordRepository;
        this.familyPlanningRepository = familyPlanningRepository;
        this.visitRepository = visitRepository;
        this.ashaHeadRepository = ashaHeadRepository;
    }

    private boolean isAshaUnderHead(Asha asha, UUID headId) {
        return asha != null && asha.getHead() != null && asha.getHead().getId().equals(headId);
    }

    private Date getCreatedAt(Object entity) {
        try {
            return (Date) entity.getClass().getMethod("getCreatedAt").invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private Asha getAsha(Object entity) {
        try {
            return (Asha) entity.getClass().getMethod("getAsha").invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private String getNotes(Object entity) {
        try {
            return (String) entity.getClass().getMethod("getNotes").invoke(entity);
        } catch (Exception e) {
            return "Recorded in system";
        }
    }

    private boolean isBetween(Date date, Date start, Date end) {
        if (date == null) return false;
        if (start != null && date.before(start)) return false;
        if (end != null && !date.before(end)) return false;
        return true;
    }

    public List<Map<String, Object>> getReportsMetrics(UUID headId) {
        LocalDateTime now = LocalDateTime.now();
        Date thisMonthStart = Date.from(now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).atZone(ZoneId.systemDefault()).toInstant());
        Date lastMonthStart = Date.from(now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).atZone(ZoneId.systemDefault()).toInstant());
        Date lastMonthEnd = thisMonthStart;

        long familiesThis = householdRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), thisMonthStart, null)).count();
        long familiesLast = householdRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), lastMonthStart, lastMonthEnd)).count();

        long ancThis = pregnancyRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), thisMonthStart, null)).count();
        long ancLast = pregnancyRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), lastMonthStart, lastMonthEnd)).count();

        long childrenThis = childRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), thisMonthStart, null)).count();
        long childrenLast = childRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), lastMonthStart, lastMonthEnd)).count();

        long vaccThis = vaccinationRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), thisMonthStart, null)).count();
        long vaccLast = vaccinationRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), lastMonthStart, lastMonthEnd)).count();

        long referralsThis = referralRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), thisMonthStart, null)).count();
        long referralsLast = referralRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && isBetween(getCreatedAt(e), lastMonthStart, lastMonthEnd)).count();

        long criticalCases = childRepository.findAll().stream().filter(e -> isAshaUnderHead(getAsha(e), headId) && "CRITICAL".equalsIgnoreCase(e.getRiskLevel())).count();

        return List.of(
            Map.of("name", "Families Surveyed", "current", familiesThis, "previous", familiesLast),
            Map.of("name", "ANC Registrations", "current", ancThis, "previous", ancLast),
            Map.of("name", "Children Measured", "current", childrenThis, "previous", childrenLast),
            Map.of("name", "Vaccinations Recorded", "current", vaccThis, "previous", vaccLast),
            Map.of("name", "Critical Cases (Total)", "current", criticalCases, "previous", 0),
            Map.of("name", "NRC Referrals", "current", referralsThis, "previous", referralsLast)
        );
    }

    public List<Map<String, Object>> getWorkersOverview(UUID headId) {
        Date monthStart = Date.from(LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).atZone(ZoneId.systemDefault()).toInstant());
        
        List<Asha> workers = ashaRepository.findAll().stream().filter(a -> a.getHead() != null && a.getHead().getId().equals(headId)).collect(Collectors.toList());
        
        List<Household> allHouseholds = householdRepository.findAll();
        List<Child> allChildren = childRepository.findAll();
        List<Pregnancy> allPregnancies = pregnancyRepository.findAll();
        List<Vaccination> allVaccinations = vaccinationRepository.findAll();

        return workers.stream().map(asha -> {
            long submissionsThisMonth = 0;
            Date lastActive = null;
            
            for (Household h : allHouseholds) {
                if (getAsha(h) != null && getAsha(h).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(h), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(h) != null && (lastActive == null || getCreatedAt(h).after(lastActive))) lastActive = getCreatedAt(h);
                }
            }
            for (Child c : allChildren) {
                if (getAsha(c) != null && getAsha(c).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(c), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(c) != null && (lastActive == null || getCreatedAt(c).after(lastActive))) lastActive = getCreatedAt(c);
                }
            }
            for (Pregnancy p : allPregnancies) {
                if (getAsha(p) != null && getAsha(p).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(p), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(p) != null && (lastActive == null || getCreatedAt(p).after(lastActive))) lastActive = getCreatedAt(p);
                }
            }
            for (Vaccination v : allVaccinations) {
                if (getAsha(v) != null && getAsha(v).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(v), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(v) != null && (lastActive == null || getCreatedAt(v).after(lastActive))) lastActive = getCreatedAt(v);
                }
            }
            
            long criticalCases = allChildren.stream().filter(c -> getAsha(c) != null && getAsha(c).getId().equals(asha.getId()) && "CRITICAL".equalsIgnoreCase(c.getRiskLevel())).count();
            
            Map<String, Object> map = new HashMap<>();
            map.put("id", asha.getId().toString());
            map.put("name", asha.getName() != null ? asha.getName() : asha.getId().toString());
            map.put("village", asha.getVillage() != null ? asha.getVillage() : "—");
            map.put("phone", asha.getPhone() != null ? asha.getPhone() : "—");
            map.put("district", "Beed");
            map.put("isActive", true);
            map.put("coveragePercent", 100);
            map.put("totalFamilies", 0);
            map.put("submissionsThisMonth", submissionsThisMonth);
            map.put("criticalCases", criticalCases);
            map.put("lastActive", lastActive);
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getWorkerActivity(UUID ashaId) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        householdRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(Map.of("moduleType", "Households", "submittedAt", getCreatedAt(e), "notes", getNotes(e)));
        });
        pregnancyRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(Map.of("moduleType", "Pregnancies", "submittedAt", getCreatedAt(e), "notes", getNotes(e)));
        });
        childRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(Map.of("moduleType", "Children", "submittedAt", getCreatedAt(e), "notes", getNotes(e)));
        });
        vaccinationRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(Map.of("moduleType", "Vaccinations", "submittedAt", getCreatedAt(e), "notes", getNotes(e)));
        });
        
        events.sort((a, b) -> {
            Date da = (Date) a.get("submittedAt");
            Date db = (Date) b.get("submittedAt");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da); // descending
        });
        
        return events.stream().limit(20).collect(Collectors.toList());
    }

    public Asha addWorker(UUID headId, String name, String phone, String village, String district) {
        AshaHead head = ashaHeadRepository.findById(headId).orElseThrow(() -> new RuntimeException("Head not found"));
        Asha asha = new Asha();
        asha.setHead(head);
        asha.setName(name);
        asha.setPhone(phone);
        asha.setVillage(village);
        asha.setDistrict(district);
        return ashaRepository.save(asha);
    }
}
