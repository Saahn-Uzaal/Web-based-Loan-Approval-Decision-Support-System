UPDATE customer_profiles
SET employment_status = CASE
    WHEN employment_status IS NULL OR TRIM(employment_status) = '' THEN NULL
    WHEN UPPER(TRIM(employment_status)) IN ('EMPLOYED', 'FULL_TIME', 'FULL TIME', 'PERMANENT', 'SALARIED')
         OR UPPER(employment_status) LIKE '%NHAN VIEN%'
         OR UPPER(employment_status) LIKE '%CHUYEN VIEN%' THEN 'EMPLOYED'
    WHEN UPPER(TRIM(employment_status)) IN ('SELF_EMPLOYED', 'SELF EMPLOYED')
         OR UPPER(employment_status) LIKE '%SELF%'
         OR UPPER(employment_status) LIKE '%FREELANCE%'
         OR UPPER(employment_status) LIKE '%TU DO%' THEN 'SELF_EMPLOYED'
    WHEN UPPER(TRIM(employment_status)) IN ('BUSINESS_OWNER', 'BUSINESS OWNER')
         OR UPPER(employment_status) LIKE '%BUSINESS%'
         OR UPPER(employment_status) LIKE '%OWNER%'
         OR UPPER(employment_status) LIKE '%KINH DOANH%'
         OR UPPER(employment_status) LIKE '%CHU DOANH NGHIEP%' THEN 'BUSINESS_OWNER'
    WHEN UPPER(TRIM(employment_status)) IN ('PART_TIME', 'PART TIME')
         OR UPPER(employment_status) LIKE '%PART%'
         OR UPPER(employment_status) LIKE '%BAN THOI GIAN%' THEN 'PART_TIME'
    WHEN UPPER(TRIM(employment_status)) IN ('CONTRACTOR', 'CONTRACT')
         OR UPPER(employment_status) LIKE '%CONTRACT%'
         OR UPPER(employment_status) LIKE '%HOP DONG%'
         OR UPPER(employment_status) LIKE '%TEMP%' THEN 'CONTRACTOR'
    WHEN UPPER(TRIM(employment_status)) = 'UNEMPLOYED'
         OR UPPER(employment_status) LIKE '%UNEMPLOY%'
         OR UPPER(employment_status) LIKE '%THAT NGHIEP%' THEN 'UNEMPLOYED'
    WHEN UPPER(TRIM(employment_status)) = 'STUDENT'
         OR UPPER(employment_status) LIKE '%STUDENT%'
         OR UPPER(employment_status) LIKE '%SINH VIEN%' THEN 'STUDENT'
    WHEN UPPER(TRIM(employment_status)) = 'RETIRED'
         OR UPPER(employment_status) LIKE '%RETIRED%'
         OR UPPER(employment_status) LIKE '%NGHI HUU%' THEN 'RETIRED'
    ELSE 'OTHER'
END;

ALTER TABLE customer_profiles
    MODIFY COLUMN employment_status ENUM(
        'EMPLOYED',
        'SELF_EMPLOYED',
        'BUSINESS_OWNER',
        'PART_TIME',
        'CONTRACTOR',
        'UNEMPLOYED',
        'STUDENT',
        'RETIRED',
        'OTHER'
    ) NULL;

UPDATE loan_application_snapshots
SET employment_status = CASE
    WHEN employment_status IS NULL OR TRIM(employment_status) = '' THEN NULL
    WHEN UPPER(TRIM(employment_status)) IN ('EMPLOYED', 'FULL_TIME', 'FULL TIME', 'PERMANENT', 'SALARIED')
         OR UPPER(employment_status) LIKE '%NHAN VIEN%'
         OR UPPER(employment_status) LIKE '%CHUYEN VIEN%' THEN 'EMPLOYED'
    WHEN UPPER(TRIM(employment_status)) IN ('SELF_EMPLOYED', 'SELF EMPLOYED')
         OR UPPER(employment_status) LIKE '%SELF%'
         OR UPPER(employment_status) LIKE '%FREELANCE%'
         OR UPPER(employment_status) LIKE '%TU DO%' THEN 'SELF_EMPLOYED'
    WHEN UPPER(TRIM(employment_status)) IN ('BUSINESS_OWNER', 'BUSINESS OWNER')
         OR UPPER(employment_status) LIKE '%BUSINESS%'
         OR UPPER(employment_status) LIKE '%OWNER%'
         OR UPPER(employment_status) LIKE '%KINH DOANH%'
         OR UPPER(employment_status) LIKE '%CHU DOANH NGHIEP%' THEN 'BUSINESS_OWNER'
    WHEN UPPER(TRIM(employment_status)) IN ('PART_TIME', 'PART TIME')
         OR UPPER(employment_status) LIKE '%PART%'
         OR UPPER(employment_status) LIKE '%BAN THOI GIAN%' THEN 'PART_TIME'
    WHEN UPPER(TRIM(employment_status)) IN ('CONTRACTOR', 'CONTRACT')
         OR UPPER(employment_status) LIKE '%CONTRACT%'
         OR UPPER(employment_status) LIKE '%HOP DONG%'
         OR UPPER(employment_status) LIKE '%TEMP%' THEN 'CONTRACTOR'
    WHEN UPPER(TRIM(employment_status)) = 'UNEMPLOYED'
         OR UPPER(employment_status) LIKE '%UNEMPLOY%'
         OR UPPER(employment_status) LIKE '%THAT NGHIEP%' THEN 'UNEMPLOYED'
    WHEN UPPER(TRIM(employment_status)) = 'STUDENT'
         OR UPPER(employment_status) LIKE '%STUDENT%'
         OR UPPER(employment_status) LIKE '%SINH VIEN%' THEN 'STUDENT'
    WHEN UPPER(TRIM(employment_status)) = 'RETIRED'
         OR UPPER(employment_status) LIKE '%RETIRED%'
         OR UPPER(employment_status) LIKE '%NGHI HUU%' THEN 'RETIRED'
    ELSE 'OTHER'
END;

ALTER TABLE loan_application_snapshots
    MODIFY COLUMN employment_status ENUM(
        'EMPLOYED',
        'SELF_EMPLOYED',
        'BUSINESS_OWNER',
        'PART_TIME',
        'CONTRACTOR',
        'UNEMPLOYED',
        'STUDENT',
        'RETIRED',
        'OTHER'
    ) NULL;
