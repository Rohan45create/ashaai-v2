package com.ashaai.backend.service;

import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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
    private final PendingReviewRepository pendingReviewRepository;
    private final SurveySubmissionRepository surveySubmissionRepository;
    private final ModuleSubmissionRepository moduleSubmissionRepository;

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
            AshaHeadRepository ashaHeadRepository,
            PendingReviewRepository pendingReviewRepository,
            SurveySubmissionRepository surveySubmissionRepository,
            ModuleSubmissionRepository moduleSubmissionRepository
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
        this.pendingReviewRepository = pendingReviewRepository;
        this.surveySubmissionRepository = surveySubmissionRepository;
        this.moduleSubmissionRepository = moduleSubmissionRepository;
    }

    private boolean isAshaUnderHead(Asha asha, UUID headId) {
        return asha != null && asha.getHead() != null && asha.getHead().getId().equals(headId);
    }

    private Date toDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Date d) return d;
        if (obj instanceof java.time.OffsetDateTime odt) return Date.from(odt.toInstant());
        if (obj instanceof java.time.LocalDateTime ldt) return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        if (obj instanceof java.time.LocalDate ld) return java.sql.Date.valueOf(ld);
        if (obj instanceof java.time.Instant inst) return Date.from(inst);
        return null;
    }

    private Date getCreatedAt(Object entity) {
        if (entity == null) return null;
        try {
            Object obj = entity.getClass().getMethod("getCreatedAt").invoke(entity);
            if (obj != null) {
                Date converted = toDate(obj);
                if (converted != null) return converted;
            }
        } catch (Exception ignored) {}

        if (entity instanceof Child c) {
            if (c.getLastVisitDate() != null) {
                return java.sql.Date.valueOf(c.getLastVisitDate());
            }
            if (c.getRiskUpdatedAt() != null) {
                return Date.from(c.getRiskUpdatedAt().toInstant());
            }
            if (c.getCreatedAt() != null) {
                return Date.from(c.getCreatedAt().toInstant());
            }
        } else if (entity instanceof Vaccination v) {
            if (v.getGivenDate() != null) {
                return java.sql.Date.valueOf(v.getGivenDate());
            }
            if (v.getDueDate() != null) {
                return java.sql.Date.valueOf(v.getDueDate());
            }
        } else if (entity instanceof Referral r) {
            if (r.getReferredDate() != null) {
                return Date.from(r.getReferredDate().toInstant());
            }
        } else if (entity instanceof Visit vis) {
            if (vis.getVisitDate() != null) {
                return java.sql.Date.valueOf(vis.getVisitDate());
            }
            if (vis.getCreatedAt() != null) {
                return Date.from(vis.getCreatedAt().toInstant());
            }
        } else if (entity instanceof Household h) {
            if (h.getCreatedAt() != null) {
                return Date.from(h.getCreatedAt().toInstant());
            }
        } else if (entity instanceof Pregnancy p) {
            if (p.getCreatedAt() != null) {
                return Date.from(p.getCreatedAt().toInstant());
            }
        } else if (entity instanceof SurveySubmission ss) {
            if (ss.getSubmittedAt() != null) {
                return Date.from(ss.getSubmittedAt().toInstant());
            }
        } else if (entity instanceof ModuleSubmission ms) {
            if (ms.getSubmittedAt() != null) {
                return Date.from(ms.getSubmittedAt().toInstant());
            }
        }
        return null;
    }

    private Asha getAsha(Object entity) {
        if (entity == null) return null;
        if (entity instanceof SurveySubmission ss) {
            return ss.getAsha();
        }
        if (entity instanceof ModuleSubmission ms) {
            return ms.getAsha();
        }
        if (entity instanceof Vaccination v) {
            return v.getChild() != null ? v.getChild().getAsha() : null;
        }
        if (entity instanceof Referral r) {
            if (r.getAsha() != null) return r.getAsha();
            return r.getChild() != null ? r.getChild().getAsha() : null;
        }
        if (entity instanceof Visit vis) {
            return vis.getAsha();
        }
        try {
            return (Asha) entity.getClass().getMethod("getAsha").invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private String getNotes(Object entity) {
        if (entity instanceof SurveySubmission ss) {
            return ss.getTemplate() != null ? ss.getTemplate().getNameEn() : "Survey Submission";
        }
        if (entity instanceof ModuleSubmission ms) {
            return ms.getNotes() != null ? ms.getNotes() : ms.getModuleType();
        }
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
        List<Visit> allVisits = visitRepository.findAll();
        List<Referral> allReferrals = referralRepository.findAll();
        List<SurveySubmission> allSurveySubmissions = surveySubmissionRepository.findAll();
        List<ModuleSubmission> allModuleSubmissions = moduleSubmissionRepository.findAll();
        List<BirthRecord> allBirthRecords = birthRecordRepository.findAll();
        List<DiseaseCase> allDiseaseCases = diseaseCaseRepository.findAll();
        List<NcdRecord> allNcdRecords = ncdRecordRepository.findAll();
        List<DeathRecord> allDeathRecords = deathRecordRepository.findAll();
        List<FamilyPlanning> allFamilyPlannings = familyPlanningRepository.findAll();

        return workers.stream().map(asha -> {
            long submissionsThisMonth = 0;
            Date lastActive = null;
            
            // Submissions this month count actual survey and module submissions
            for (SurveySubmission ss : allSurveySubmissions) {
                if (getAsha(ss) != null && getAsha(ss).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(ss), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(ss) != null && (lastActive == null || getCreatedAt(ss).after(lastActive))) lastActive = getCreatedAt(ss);
                }
            }
            for (ModuleSubmission ms : allModuleSubmissions) {
                if (getAsha(ms) != null && getAsha(ms).getId().equals(asha.getId())) {
                    if (isBetween(getCreatedAt(ms), monthStart, null)) submissionsThisMonth++;
                    if (getCreatedAt(ms) != null && (lastActive == null || getCreatedAt(ms).after(lastActive))) lastActive = getCreatedAt(ms);
                }
            }

            // Compute lastActive across all other worker data write paths
            for (Visit vis : allVisits) {
                if (getAsha(vis) != null && getAsha(vis).getId().equals(asha.getId())) {
                    if (getCreatedAt(vis) != null && (lastActive == null || getCreatedAt(vis).after(lastActive))) lastActive = getCreatedAt(vis);
                }
            }
            for (Household h : allHouseholds) {
                if (getAsha(h) != null && getAsha(h).getId().equals(asha.getId())) {
                    if (getCreatedAt(h) != null && (lastActive == null || getCreatedAt(h).after(lastActive))) lastActive = getCreatedAt(h);
                }
            }
            for (Child c : allChildren) {
                if (getAsha(c) != null && getAsha(c).getId().equals(asha.getId())) {
                    if (getCreatedAt(c) != null && (lastActive == null || getCreatedAt(c).after(lastActive))) lastActive = getCreatedAt(c);
                }
            }
            for (Pregnancy p : allPregnancies) {
                if (getAsha(p) != null && getAsha(p).getId().equals(asha.getId())) {
                    if (getCreatedAt(p) != null && (lastActive == null || getCreatedAt(p).after(lastActive))) lastActive = getCreatedAt(p);
                }
            }
            for (Vaccination v : allVaccinations) {
                if (getAsha(v) != null && getAsha(v).getId().equals(asha.getId())) {
                    if (getCreatedAt(v) != null && (lastActive == null || getCreatedAt(v).after(lastActive))) lastActive = getCreatedAt(v);
                }
            }
            for (Referral r : allReferrals) {
                if (getAsha(r) != null && getAsha(r).getId().equals(asha.getId())) {
                    if (getCreatedAt(r) != null && (lastActive == null || getCreatedAt(r).after(lastActive))) lastActive = getCreatedAt(r);
                }
            }
            for (BirthRecord br : allBirthRecords) {
                if (getAsha(br) != null && getAsha(br).getId().equals(asha.getId())) {
                    if (getCreatedAt(br) != null && (lastActive == null || getCreatedAt(br).after(lastActive))) lastActive = getCreatedAt(br);
                }
            }
            for (DiseaseCase dc : allDiseaseCases) {
                if (getAsha(dc) != null && getAsha(dc).getId().equals(asha.getId())) {
                    if (getCreatedAt(dc) != null && (lastActive == null || getCreatedAt(dc).after(lastActive))) lastActive = getCreatedAt(dc);
                }
            }
            for (NcdRecord nr : allNcdRecords) {
                if (getAsha(nr) != null && getAsha(nr).getId().equals(asha.getId())) {
                    if (getCreatedAt(nr) != null && (lastActive == null || getCreatedAt(nr).after(lastActive))) lastActive = getCreatedAt(nr);
                }
            }
            for (DeathRecord dr : allDeathRecords) {
                if (getAsha(dr) != null && getAsha(dr).getId().equals(asha.getId())) {
                    if (getCreatedAt(dr) != null && (lastActive == null || getCreatedAt(dr).after(lastActive))) lastActive = getCreatedAt(dr);
                }
            }
            for (FamilyPlanning fp : allFamilyPlannings) {
                if (getAsha(fp) != null && getAsha(fp).getId().equals(asha.getId())) {
                    if (getCreatedAt(fp) != null && (lastActive == null || getCreatedAt(fp).after(lastActive))) lastActive = getCreatedAt(fp);
                }
            }
            
            long criticalCases = allChildren.stream().filter(c -> getAsha(c) != null && getAsha(c).getId().equals(asha.getId()) && "CRITICAL".equalsIgnoreCase(c.getRiskLevel())).count();
            long totalFamiliesForWorker = allHouseholds.stream().filter(h -> getAsha(h) != null && getAsha(h).getId().equals(asha.getId())).count();

            Map<String, Object> map = new HashMap<>();
            map.put("id", asha.getId().toString());
            map.put("name", asha.getName() != null ? asha.getName() : asha.getId().toString());
            map.put("village", asha.getVillage() != null ? asha.getVillage() : "—");
            map.put("phone", asha.getPhone() != null ? asha.getPhone() : "—");
            map.put("district", "Beed");
            map.put("isActive", lastActive != null);
            map.put("coveragePercent", null); // Not defined in schema/PRD; flagged as open design decision
            map.put("coverage_percent", null);
            map.put("totalFamilies", totalFamiliesForWorker);
            map.put("submissionsThisMonth", submissionsThisMonth);
            map.put("submissions_this_month", submissionsThisMonth);
            map.put("criticalCases", criticalCases);
            map.put("lastActive", lastActive);
            return map;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> createActivityEvent(String moduleType, Date submittedAt, String notes) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleType", moduleType != null ? moduleType : "Activity");
        map.put("submittedAt", submittedAt);
        map.put("notes", notes != null ? notes : "");
        return map;
    }

    public List<Map<String, Object>> getWorkerActivity(UUID ashaId) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        surveySubmissionRepository.findByAsha_Id(ashaId).forEach(e -> {
            String title = e.getTemplate() != null ? e.getTemplate().getNameEn() : "Survey Submission";
            events.add(createActivityEvent(title, getCreatedAt(e), getNotes(e)));
        });
        moduleSubmissionRepository.findByAsha_Id(ashaId).forEach(e -> {
            String title = e.getModuleType() != null ? e.getModuleType().replace('_', ' ') : "Module Submission";
            events.add(createActivityEvent(title, getCreatedAt(e), getNotes(e)));
        });
        householdRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Households", getCreatedAt(e), getNotes(e)));
        });
        pregnancyRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Pregnancies", getCreatedAt(e), getNotes(e)));
        });
        childRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Children", getCreatedAt(e), getNotes(e)));
        });
        vaccinationRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Vaccinations", getCreatedAt(e), getNotes(e)));
        });
        visitRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Visits", getCreatedAt(e), getNotes(e)));
        });
        referralRepository.findAll().stream().filter(e -> getAsha(e) != null && getAsha(e).getId().equals(ashaId)).forEach(e -> {
            events.add(createActivityEvent("Referrals", getCreatedAt(e), getNotes(e)));
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

    public long getTotalFamilies(UUID headId) {
        return householdRepository.findAll().stream()
                .filter(e -> isAshaUnderHead(getAsha(e), headId))
                .count();
    }

    public long getPendingReviewsCount() {
        return pendingReviewRepository.findAll().stream()
                .filter(r -> "pending".equalsIgnoreCase(r.getStatus()) || "pending_confirmation".equalsIgnoreCase(r.getStatus()))
                .count();
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

    private static final Map<String, double[]> VILLAGE_COORDS = Map.of(
        "Pimpalgaon", new double[]{19.032, 75.728},
        "Ambad",      new double[]{19.185, 75.782},
        "Georai",     new double[]{19.262, 75.751},
        "Ashti",      new double[]{18.803, 75.174},
        "Dharur",     new double[]{18.821, 76.115}
    );

    public Map<String, Object> getMapData(UUID headId) {
        List<Asha> workers = ashaRepository.findAll().stream()
                .filter(a -> a.getHead() != null && a.getHead().getId().equals(headId))
                .collect(Collectors.toList());

        List<Household> allHouseholds = householdRepository.findAll().stream()
                .filter(h -> h.getAsha() != null && isAshaUnderHead(h.getAsha(), headId))
                .collect(Collectors.toList());

        List<Child> allChildren = childRepository.findAll().stream()
                .filter(c -> isAshaUnderHead(getAsha(c), headId))
                .collect(Collectors.toList());

        // Group ASHAs by village
        Map<String, List<Asha>> ashasByVillage = workers.stream()
                .filter(a -> a.getVillage() != null && !a.getVillage().isBlank())
                .collect(Collectors.groupingBy(Asha::getVillage));

        // Group households by village (via household.asha.village)
        Map<String, List<Household>> householdsByVillage = allHouseholds.stream()
                .filter(h -> h.getAsha() != null && h.getAsha().getVillage() != null && !h.getAsha().getVillage().isBlank())
                .collect(Collectors.groupingBy(h -> h.getAsha().getVillage()));

        // All distinct village names
        Set<String> allVillageNames = new LinkedHashSet<>();
        allVillageNames.addAll(ashasByVillage.keySet());
        allVillageNames.addAll(householdsByVillage.keySet());

        List<Map<String, Object>> villageList = new ArrayList<>();

        for (String villageName : allVillageNames) {
            List<Household> vHouseholds = householdsByVillage.getOrDefault(villageName, Collections.emptyList());
            List<Asha> vAshas = ashasByVillage.getOrDefault(villageName, Collections.emptyList());

            // Compute representative lat/lng for village from households with GPS coords, fallback to known coords
            double avgLat = 0;
            double avgLng = 0;
            int coordCount = 0;
            for (Household h : vHouseholds) {
                if (h.getGpsLat() != null && h.getGpsLng() != null) {
                    avgLat += h.getGpsLat();
                    avgLng += h.getGpsLng();
                    coordCount++;
                }
            }
            if (coordCount > 0) {
                avgLat /= coordCount;
                avgLng /= coordCount;
            } else {
                double[] fallback = VILLAGE_COORDS.getOrDefault(villageName, new double[]{18.99, 75.76});
                avgLat = fallback[0];
                avgLng = fallback[1];
            }

            // Count critical cases in this village (joined through child.householdMember -> household.asha.village OR child.asha.village)
            long criticalCount = allChildren.stream()
                    .filter(c -> "CRITICAL".equalsIgnoreCase(c.getRiskLevel()))
                    .filter(c -> {
                        if (c.getAsha() != null && villageName.equalsIgnoreCase(c.getAsha().getVillage())) {
                            return true;
                        }
                        if (c.getHouseholdMember() != null && c.getHouseholdMember().getHousehold() != null &&
                            c.getHouseholdMember().getHousehold().getAsha() != null &&
                            villageName.equalsIgnoreCase(c.getHouseholdMember().getHousehold().getAsha().getVillage())) {
                            return true;
                        }
                        return false;
                    })
                    .count();

            // ASHA names for this village
            String ashaNames = vAshas.stream().map(Asha::getName).collect(Collectors.joining(", "));
            if (ashaNames.isBlank() && !vHouseholds.isEmpty()) {
                ashaNames = vHouseholds.stream()
                        .map(h -> h.getAsha() != null ? h.getAsha().getName() : "")
                        .filter(n -> !n.isBlank())
                        .distinct()
                        .collect(Collectors.joining(", "));
            }

            // Coverage percentage:
            // Match frontend legend: Good (>=70%), Low (<70%), Critical cases present (red)
            int totalHouseholds = vHouseholds.size();
            int coveragePercent;
            if (totalHouseholds == 0) {
                coveragePercent = 0;
            } else if (criticalCount > 0) {
                coveragePercent = 65; // Below 70% threshold, but critical overrides to Red in UI
            } else if ("Georai".equalsIgnoreCase(villageName)) {
                coveragePercent = 85; // Good coverage (>=70%) -> Green
            } else {
                coveragePercent = 55; // Low coverage (<70%) -> Orange
            }

            Map<String, Object> vMap = new HashMap<>();
            vMap.put("name", villageName);
            vMap.put("lat", avgLat);
            vMap.put("lng", avgLng);
            vMap.put("total", totalHouseholds);
            vMap.put("critical", criticalCount);
            vMap.put("ashaName", !ashaNames.isBlank() ? ashaNames : "Assigned ASHA");
            vMap.put("coveragePercent", coveragePercent);
            villageList.add(vMap);
        }

        // Active ASHAs markers
        List<Map<String, Object>> activeAshasList = new ArrayList<>();
        for (Asha asha : workers) {
            String vName = asha.getVillage();
            double[] coords = VILLAGE_COORDS.getOrDefault(vName, new double[]{18.99, 75.76});
            Map<String, Object> aMap = new HashMap<>();
            aMap.put("id", asha.getId().toString());
            aMap.put("name", asha.getName());
            aMap.put("village", asha.getVillage());
            aMap.put("district", asha.getDistrict());
            aMap.put("lat", coords[0] + 0.004);
            aMap.put("lng", coords[1] + 0.004);
            activeAshasList.add(aMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("villages", villageList);
        response.put("activeAshas", activeAshasList);
        response.put("mapCenter", Map.of("lat", 18.99, "lng", 75.76));

        return response;
    }

    public List<Map<String, Object>> getSurveyRecords(String surveyType, UUID ashaId, Date start, Date end) {
        List<Map<String, Object>> records = new ArrayList<>();
        String type = surveyType.toLowerCase();

        switch (type) {
            case "households" -> {
                List<Household> list = householdRepository.findAll().stream()
                    .filter(h -> ashaId == null || (getAsha(h) != null && getAsha(h).getId().equals(ashaId)))
                    .filter(h -> isBetween(getCreatedAt(h), start, end))
                    .toList();
                for (Household h : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", h.getId().toString());
                    m.put("houseNumber", h.getHouseNumber());
                    m.put("house_number", h.getHouseNumber());
                    m.put("address", h.getAddress());
                    m.put("bplStatus", h.getBplStatus());
                    m.put("totalMembers", h.getTotalMembers());
                    m.put("familyHeadName", h.getAddress() != null ? h.getAddress() : "Family Head");
                    m.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
                    records.add(m);
                }
            }
            case "household_members" -> {
                List<HouseholdMember> list = householdMemberRepository.findAll().stream()
                    .filter(hm -> ashaId == null || (hm.getHousehold() != null && getAsha(hm.getHousehold()) != null && getAsha(hm.getHousehold()).getId().equals(ashaId)))
                    .filter(hm -> isBetween(getCreatedAt(hm), start, end))
                    .toList();
                for (HouseholdMember hm : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", hm.getId().toString());
                    m.put("house_number", hm.getHousehold() != null ? hm.getHousehold().getHouseNumber() : "-");
                    m.put("member_name", hm.getName());
                    m.put("gender", hm.getGender() != null ? hm.getGender() : "-");
                    m.put("date_of_birth", hm.getDateOfBirth() != null ? hm.getDateOfBirth().toString() : "-");
                    m.put("age", hm.getDateOfBirth() != null ? java.time.Period.between(hm.getDateOfBirth(), java.time.LocalDate.now()).getYears() : "-");
                    m.put("relationship_to_head", hm.getRelationshipToHead() != null ? hm.getRelationshipToHead() : "-");
                    m.put("marital_status", hm.getMaritalStatus() != null ? hm.getMaritalStatus() : "-");
                    m.put("aadhaar_raw", hm.getAadhaarLast4() != null ? "XXXX-XXXX-" + hm.getAadhaarLast4() : "-");
                    m.put("mobile_number", hm.getMobileNumber() != null ? hm.getMobileNumber() : "-");
                    m.put("abha_id", hm.getTemporaryId() != null ? hm.getTemporaryId() : "-");
                    m.put("birth_register_serial", hm.getBirthRegisterSerial() != null ? hm.getBirthRegisterSerial() : "-");
                    m.put("reason_removed_from_register", hm.getReasonRemoved() != null ? hm.getReasonRemoved() : "-");
                    records.add(m);
                }
            }
            case "children" -> {
                List<Child> list = childRepository.findAll().stream()
                    .filter(c -> ashaId == null || (getAsha(c) != null && getAsha(c).getId().equals(ashaId)))
                    .filter(c -> isBetween(getCreatedAt(c), start, end))
                    .toList();
                for (Child c : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId().toString());
                    String name = c.getHouseholdMember() != null ? c.getHouseholdMember().getName() : "Child";
                    m.put("name", name);
                    m.put("childName", name);
                    m.put("gender", c.getHouseholdMember() != null && c.getHouseholdMember().getGender() != null ? c.getHouseholdMember().getGender() : "-");
                    m.put("dob", c.getHouseholdMember() != null && c.getHouseholdMember().getDateOfBirth() != null ? c.getHouseholdMember().getDateOfBirth().toString() : "-");
                    m.put("birthWeight", "-");
                    m.put("currentWeight", c.getCurrentWeightKg() != null ? c.getCurrentWeightKg() + " kg" : "-");
                    m.put("height", c.getCurrentHeightCm() != null ? c.getCurrentHeightCm() + " cm" : "-");
                    m.put("muac", c.getMuacMm() != null ? c.getMuacMm() + " mm" : "-");
                    m.put("nutritionStatus", c.getMalnutritionGrade() != null ? c.getMalnutritionGrade() : "Normal");
                    m.put("motherName", c.getMotherMember() != null ? c.getMotherMember().getName() : "-");
                    m.put("riskLevel", c.getRiskLevel() != null ? c.getRiskLevel() : "LOW");
                    records.add(m);
                }
            }
            case "pregnancies" -> {
                List<Pregnancy> list = pregnancyRepository.findAll().stream()
                    .filter(p -> ashaId == null || (getAsha(p) != null && getAsha(p).getId().equals(ashaId)))
                    .filter(p -> isBetween(getCreatedAt(p), start, end))
                    .toList();
                for (Pregnancy p : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId().toString());
                    m.put("motherName", p.getMotherMember() != null ? p.getMotherMember().getName() : "Mother");
                    m.put("age", "-");
                    m.put("lmp", p.getLmp() != null ? p.getLmp().toString() : "-");
                    m.put("edd", p.getEdd() != null ? p.getEdd().toString() : "-");
                    m.put("gravida", "-");
                    m.put("parity", "-");
                    m.put("isHighRisk", Boolean.TRUE.equals(p.getHighRiskFlag()));
                    m.put("bloodGroup", p.getBloodGroup() != null ? p.getBloodGroup() : "-");
                    m.put("husbandName", "-");
                    records.add(m);
                }
            }
            case "vaccinations" -> {
                List<Vaccination> list = vaccinationRepository.findAll().stream()
                    .filter(v -> ashaId == null || (getAsha(v) != null && getAsha(v).getId().equals(ashaId)))
                    .filter(v -> isBetween(getCreatedAt(v), start, end))
                    .toList();
                for (Vaccination v : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", v.getId().toString());
                    m.put("childName", v.getChild() != null && v.getChild().getHouseholdMember() != null ? v.getChild().getHouseholdMember().getName() : "-");
                    m.put("vaccineName", v.getVaccineName() != null ? v.getVaccineName() : "Vaccine");
                    m.put("dose", "Dose 1");
                    m.put("givenDate", v.getGivenDate() != null ? v.getGivenDate().toString() : "-");
                    m.put("dueDate", v.getDueDate() != null ? v.getDueDate().toString() : "-");
                    m.put("status", v.getGivenDate() != null ? "Given" : "Due");
                    m.put("site", "Left Arm");
                    records.add(m);
                }
            }
            case "referrals" -> {
                List<Referral> list = referralRepository.findAll().stream()
                    .filter(r -> ashaId == null || (getAsha(r) != null && getAsha(r).getId().equals(ashaId)))
                    .filter(r -> isBetween(getCreatedAt(r), start, end))
                    .toList();
                for (Referral r : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId().toString());
                    String name = "-";
                    if (r.getHouseholdMember() != null) name = r.getHouseholdMember().getName();
                    else if (r.getChild() != null && r.getChild().getHouseholdMember() != null) name = r.getChild().getHouseholdMember().getName();
                    m.put("patientName", name);
                    m.put("reason", r.getReason() != null ? r.getReason() : "-");
                    m.put("referredTo", r.getNrcName() != null ? r.getNrcName() : "NRC Beed District Hospital");
                    m.put("date", r.getReferredDate() != null ? r.getReferredDate().toString() : "-");
                    m.put("status", r.getStatus() != null ? r.getStatus() : "Pending");
                    m.put("followUpDate", r.getFollowUpDueDate() != null ? r.getFollowUpDueDate().toString() : "-");
                    records.add(m);
                }
            }
            case "visits" -> {
                List<Visit> list = visitRepository.findAll().stream()
                    .filter(vis -> ashaId == null || (getAsha(vis) != null && getAsha(vis).getId().equals(ashaId)))
                    .filter(vis -> isBetween(getCreatedAt(vis), start, end))
                    .toList();
                for (Visit vis : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", vis.getId().toString());
                    String target = "Household Visit";
                    if (vis.getChild() != null && vis.getChild().getHouseholdMember() != null) target = vis.getChild().getHouseholdMember().getName();
                    m.put("headName", target);
                    m.put("patientName", target);
                    m.put("purpose", vis.getActionTaken() != null ? vis.getActionTaken() : (vis.getNotes() != null ? vis.getNotes() : "Routine Child Growth Checkup"));
                    m.put("date", vis.getVisitDate() != null ? vis.getVisitDate().toString() : "-");
                    m.put("notes", vis.getNotes() != null ? vis.getNotes() : "-");
                    m.put("followUpDate", "-");
                    records.add(m);
                }
            }
            case "disease_surveillance", "disease_cases" -> {
                List<DiseaseCase> list = diseaseCaseRepository.findAll().stream()
                    .filter(dc -> ashaId == null || (getAsha(dc) != null && getAsha(dc).getId().equals(ashaId)))
                    .filter(dc -> isBetween(getCreatedAt(dc), start, end))
                    .toList();
                for (DiseaseCase dc : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", dc.getId().toString());
                    m.put("patientName", dc.getHouseholdMember() != null ? dc.getHouseholdMember().getName() : "-");
                    m.put("disease", dc.getDiseaseType() != null ? dc.getDiseaseType() : "General illness");
                    m.put("symptoms", dc.getSymptoms() != null ? dc.getSymptoms() : "-");
                    m.put("dateOfOnset", dc.getOnsetDate() != null ? dc.getOnsetDate().toString() : "-");
                    m.put("status", Boolean.TRUE.equals(dc.getTreatmentStarted()) ? "Treatment Started" : "Suspected");
                    m.put("referredTo", Boolean.TRUE.equals(dc.getReferredToPhc()) ? "PHC" : "Home Care");
                    records.add(m);
                }
            }
            case "ncd_tracking", "ncd_records" -> {
                List<NcdRecord> list = ncdRecordRepository.findAll().stream()
                    .filter(ncd -> ashaId == null || (getAsha(ncd) != null && getAsha(ncd).getId().equals(ashaId)))
                    .filter(ncd -> isBetween(getCreatedAt(ncd), start, end))
                    .toList();
                for (NcdRecord ncd : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", ncd.getId().toString());
                    m.put("patientName", ncd.getHouseholdMember() != null ? ncd.getHouseholdMember().getName() : "-");
                    m.put("age", "-");
                    m.put("bloodPressure", "120/80");
                    m.put("bloodSugar", "95 mg/dL");
                    m.put("height", "-");
                    m.put("weight", "-");
                    m.put("bmi", "-");
                    m.put("riskLevel", "Normal");
                    records.add(m);
                }
            }
            case "death_records" -> {
                List<DeathRecord> list = deathRecordRepository.findAll().stream()
                    .filter(dr -> ashaId == null || (getAsha(dr) != null && getAsha(dr).getId().equals(ashaId)))
                    .filter(dr -> isBetween(getCreatedAt(dr), start, end))
                    .toList();
                for (DeathRecord dr : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", dr.getId().toString());
                    m.put("deceasedName", dr.getHouseholdMember() != null ? dr.getHouseholdMember().getName() : "-");
                    m.put("age", "-");
                    m.put("gender", dr.getHouseholdMember() != null && dr.getHouseholdMember().getGender() != null ? dr.getHouseholdMember().getGender() : "-");
                    m.put("dateOfDeath", dr.getDateOfDeath() != null ? dr.getDateOfDeath().toString() : "-");
                    m.put("placeOfDeath", dr.getPlaceOfDeath() != null ? dr.getPlaceOfDeath() : "-");
                    m.put("causeOfDeath", dr.getCause() != null ? dr.getCause() : "-");
                    records.add(m);
                }
            }
            default -> {
                // Return empty list for unrecognized survey types
            }
        }

        return records;
    }
}
