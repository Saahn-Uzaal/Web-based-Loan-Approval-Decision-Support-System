import {
  ArrowForwardRounded as ArrowIcon,

  AutoGraphRounded as ScoreIcon,
  CheckCircleRounded as CheckIcon,

  ManageSearchRounded as VerifyIcon,
  PaymentsRounded as PaymentIcon,
  SavingsRounded as LoanIcon,
  ShieldRounded as ShieldIcon
} from "@mui/icons-material";
import {
  Box,
  Button,
  Chip,
  Container,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { labelRole } from "@/shared/utils/labels";

const highlights = [
  {
    title: "DSS chấm điểm minh bạch",
    description: "Kết hợp DTI, thu nhập, lịch sử tín dụng, tài sản bảo đảm và xác minh để đưa ra khuyến nghị rõ ràng.",
    icon: <ScoreIcon fontSize="small" />,
    color: "#b85c38"
  },
  {
    title: "KYC / AML / Fraud trong cùng luồng",
    description: "Nhân viên kiểm tra hồ sơ, định danh, thu nhập và cờ gian lận ngay trên một màn hình thẩm định.",
    icon: <VerifyIcon fontSize="small" />,
    color: "#0f766e"
  },
  {
    title: "Từ phê duyệt đến thanh toán",
    description: "Sinh hợp đồng vay, theo dõi dư nợ và cập nhật payment rating sau mỗi kỳ thanh toán.",
    icon: <PaymentIcon fontSize="small" />,
    color: "#0f4c81"
  }
];

const processSteps = [
  {
    step: "01",
    title: "Nộp hồ sơ tài chính",
    description: "Khách hàng cập nhật hồ sơ cá nhân, khai báo các khoản nợ và tạo yêu cầu vay."
  },
  {
    step: "02",
    title: "Hệ thống chấm điểm",
    description: "DSS đánh giá credit score, risk rank và recommendation dựa trên dữ liệu thực tế."
  },
  {
    step: "03",
    title: "Nhân viên xác minh",
    description: "KYC, AML, income verification và fraud check được tổng hợp trong quy trình thẩm định."
  },
  {
    step: "04",
    title: "Ra quyết định và theo dõi",
    description: "Phê duyệt hoặc từ chối, sinh hợp đồng, ghi nhận thanh toán và audit log."
  }
];

const roleCards = [
  {
    role: "CUSTOMER",
    title: "Khách hàng",
    items: [
      "Cập nhật hồ sơ tài chính và khoản nợ",
      "Tạo hồ sơ vay và theo dõi trạng thái",
      "Xem hợp đồng và ghi nhận thanh toán"
    ],
    color: "#b85c38"
  },
  {
    role: "STAFF",
    title: "Nhân viên",
    items: [
      "Xem hàng đợi thẩm định",
      "Cập nhật xác minh KYC / AML / Fraud",
      "Ra quyết định APPROVE / REJECT / ESCALATE"
    ],
    color: "#0f766e"
  },
  {
    role: "ADMIN",
    title: "Quản trị",
    items: [
      "Quản lý tài khoản người dùng",
      "Giám sát cấu trúc phân quyền",
      "Kiểm soát nền tảng vận hành chung"
    ],
    color: "#0f4c81"
  }
];

function SectionHeading({ eyebrow, title, description, align = "left" }) {
  return (
    <Stack spacing={1.5} sx={{ textAlign: align }}>
      <Typography
        variant="overline"
        sx={{
          letterSpacing: "0.18em",
          fontWeight: 800,
          color: "var(--landing-accent)"
        }}
      >
        {eyebrow}
      </Typography>
      <Typography
        sx={{
          fontFamily: "var(--landing-display)",
          fontSize: { xs: "2rem", md: "2.7rem" },
          lineHeight: 1.02,
          fontWeight: 700,
          maxWidth: align === "center" ? 760 : 620,
          mx: align === "center" ? "auto" : 0
        }}
      >
        {title}
      </Typography>
      <Typography
        color="text.secondary"
        sx={{
          fontSize: { xs: "1rem", md: "1.05rem" },
          maxWidth: align === "center" ? 720 : 620,
          mx: align === "center" ? "auto" : 0
        }}
      >
        {description}
      </Typography>
    </Stack>
  );
}

function HighlightCard({ title, description, icon, color, index }) {
  return (
    <Paper
      sx={{
        p: 3,
        height: "100%",
        borderRadius: 4,
        border: `1px solid ${alpha(color, 0.18)}`,
        background: `linear-gradient(180deg, ${alpha(color, 0.08)} 0%, rgba(255,255,255,0.96) 65%)`,
        animation: `riseIn 700ms ease ${index * 120}ms both`
      }}
    >
      <Stack spacing={2}>
        <Box
          sx={{
            width: 52,
            height: 52,
            borderRadius: 3,
            bgcolor: alpha(color, 0.12),
            color,
            display: "grid",
            placeItems: "center"
          }}
        >
          {icon}
        </Box>
        <Typography variant="h6" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
          {title}
        </Typography>
        <Typography color="text.secondary">
          {description}
        </Typography>
      </Stack>
    </Paper>
  );
}

function ProcessCard({ step, title, description }) {
  return (
    <Paper
      sx={{
        p: 3,
        height: "100%",
        borderRadius: 4,
        border: "1px solid rgba(9,33,58,0.08)",
        backgroundColor: "rgba(255,255,255,0.92)"
      }}
    >
      <Stack spacing={2}>
        <Chip
          label={step}
          sx={{
            width: "fit-content",
            bgcolor: "rgba(184,92,56,0.12)",
            color: "var(--landing-accent)",
            fontWeight: 800
          }}
        />
        <Typography variant="h6" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
          {title}
        </Typography>
        <Typography color="text.secondary">
          {description}
        </Typography>
      </Stack>
    </Paper>
  );
}

export default function LandingPage() {
  const { isAuthenticated, user } = useAuth();
  const primaryActionTo = isAuthenticated ? "/dashboard" : "/login";
  const primaryActionLabel = isAuthenticated ? "Vào bảng điều khiển" : "Đăng nhập hệ thống";

  return (
    <Box
      sx={{
        "--landing-bg": "#f6efe3",
        "--landing-ink": "#09213a",
        "--landing-surface": "#fffaf1",
        "--landing-accent": "#b85c38",
        "--landing-accent-2": "#0f766e",
        "--landing-accent-3": "#0f4c81",
        "--landing-display": '"Aptos Display", "Trebuchet MS", sans-serif',
        minHeight: "100vh",
        color: "var(--landing-ink)",
        background: `
          radial-gradient(circle at top left, rgba(184,92,56,0.18), transparent 30%),
          radial-gradient(circle at top right, rgba(15,118,110,0.16), transparent 28%),
          linear-gradient(180deg, #fcf5ea 0%, #f2ecdf 100%)
        `,
        "@keyframes riseIn": {
          from: {
            opacity: 0,
            transform: "translateY(24px)"
          },
          to: {
            opacity: 1,
            transform: "translateY(0)"
          }
        },
        "@keyframes floatDrift": {
          "0%": {
            transform: "translateY(0)"
          },
          "50%": {
            transform: "translateY(-8px)"
          },
          "100%": {
            transform: "translateY(0)"
          }
        },
        "@keyframes pulseDot": {
          "0%": {
            boxShadow: `0 0 0 0 ${alpha("#0f766e", 0.35)}`
          },
          "100%": {
            boxShadow: `0 0 0 16px ${alpha("#0f766e", 0)}`
          }
        }
      }}
    >
      <Box sx={{ position: "sticky", top: 0, zIndex: 10, backdropFilter: "blur(18px)" }}>
        <Container maxWidth="xl">
          <Stack
            direction={{ xs: "column", md: "row" }}
            alignItems={{ xs: "flex-start", md: "center" }}
            justifyContent="space-between"
            spacing={2}
            sx={{
              py: 2,
              borderBottom: "1px solid rgba(9,33,58,0.08)"
            }}
          >
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Box
                sx={{
                  width: 44,
                  height: 44,
                  borderRadius: 3,
                  bgcolor: "var(--landing-ink)",
                  color: "#fff",
                  display: "grid",
                  placeItems: "center"
                }}
              >
                <LoanIcon />
              </Box>
              <Stack spacing={0.25}>
                <Typography sx={{ fontFamily: "var(--landing-display)", fontWeight: 800 }}>
                  Loan Approval DSS
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Nền tảng xét duyệt khoản vay theo quy trình nghiệp vụ
                </Typography>
              </Stack>
            </Stack>

            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.25} alignItems={{ sm: "center" }}>
              <Button color="inherit" component="a" href="#gia-tri">
                Giải pháp
              </Button>
              <Button color="inherit" component="a" href="#quy-trinh">
                Quy trình
              </Button>
              <Button color="inherit" component="a" href="#vai-tro">
                Vai trò
              </Button>
              {isAuthenticated && (
                <Chip
                  label={`Đang đăng nhập: ${labelRole(user?.role || "GUEST")}`}
                  sx={{
                    bgcolor: "rgba(9,33,58,0.06)",
                    color: "var(--landing-ink)",
                    fontWeight: 700
                  }}
                />
              )}
              <Button
                component={RouterLink}
                to={primaryActionTo}
                variant="contained"
                endIcon={<ArrowIcon />}
                sx={{
                  px: 2.25,
                  py: 1.2,
                  borderRadius: 999,
                  bgcolor: "var(--landing-ink)",
                  "&:hover": {
                    bgcolor: "#0f355c"
                  }
                }}
              >
                {primaryActionLabel}
              </Button>
            </Stack>
          </Stack>
        </Container>
      </Box>

      <Container maxWidth="xl" sx={{ py: { xs: 5, md: 8 } }}>
        <Grid container spacing={{ xs: 4, md: 6 }} alignItems="center">
          <Grid item xs={12} md={7}>
            <Stack spacing={3}>
              <Chip
                label="Loan workflow / DSS / KYC / Repayment"
                sx={{
                  alignSelf: "flex-start",
                  bgcolor: "rgba(184,92,56,0.12)",
                  color: "var(--landing-accent)",
                  fontWeight: 800,
                  letterSpacing: "0.06em"
                }}
              />
              <Typography
                sx={{
                  fontFamily: "var(--landing-display)",
                  fontSize: { xs: "2.8rem", md: "5rem" },
                  lineHeight: 0.95,
                  fontWeight: 800,
                  letterSpacing: "-0.04em",
                  maxWidth: 780,
                  animation: "riseIn 650ms ease both"
                }}
              >
                Một điểm vào chung cho toàn bộ quy trình xét duyệt khoản vay.
              </Typography>
              <Typography
                sx={{
                  maxWidth: 640,
                  color: alpha("#09213a", 0.76),
                  fontSize: { xs: "1.02rem", md: "1.15rem" },
                  animation: "riseIn 780ms ease both"
                }}
              >
                Trang chủ này đóng vai trò cửa vào trước khi đăng nhập: giới thiệu hệ thống, giải thích quy trình,
                làm rõ vai trò người dùng và dẫn người dùng đến đúng trải nghiệm khi bắt đầu xác thực.
              </Typography>

              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} sx={{ animation: "riseIn 900ms ease both" }}>
                <Button
                  component={RouterLink}
                  to={primaryActionTo}
                  variant="contained"
                  size="large"
                  endIcon={<ArrowIcon />}
                  sx={{
                    px: 3,
                    py: 1.4,
                    borderRadius: 999,
                    bgcolor: "var(--landing-accent)",
                    "&:hover": {
                      bgcolor: "#9d4d2f"
                    }
                  }}
                >
                  {primaryActionLabel}
                </Button>
                <Button
                  component="a"
                  href="#quy-trinh"
                  variant="outlined"
                  size="large"
                  sx={{
                    px: 3,
                    py: 1.4,
                    borderRadius: 999,
                    borderColor: alpha("#09213a", 0.18),
                    color: "var(--landing-ink)"
                  }}
                >
                  Xem quy trình xử lý
                </Button>
              </Stack>

              <Grid container spacing={2} sx={{ pt: 1 }}>
                {[
                  { label: "Vai trò vận hành", value: "3 lớp" },
                  { label: "Khối nghiệp vụ", value: "DSS + Risk + Verification" },
                  { label: "Theo dõi sau duyệt", value: "Contract + Repayment" }
                ].map((metric, index) => (
                  <Grid item xs={12} sm={4} key={metric.label}>
                    <Paper
                      sx={{
                        p: 2.25,
                        borderRadius: 4,
                        backgroundColor: "rgba(255,255,255,0.74)",
                        border: "1px solid rgba(9,33,58,0.08)",
                        animation: `riseIn 880ms ease ${index * 120}ms both`
                      }}
                    >
                      <Typography variant="body2" color="text.secondary">
                        {metric.label}
                      </Typography>
                      <Typography sx={{ fontFamily: "var(--landing-display)", fontSize: "1.45rem", fontWeight: 700 }}>
                        {metric.value}
                      </Typography>
                    </Paper>
                  </Grid>
                ))}
              </Grid>
            </Stack>
          </Grid>

          <Grid item xs={12} md={5}>
            <Box
              sx={{
                position: "relative",
                minHeight: { xs: 420, md: 520 },
                display: "grid",
                placeItems: "center"
              }}
            >
              <Paper
                sx={{
                  p: 3,
                  width: "100%",
                  maxWidth: 520,
                  borderRadius: 6,
                  background: "linear-gradient(180deg, rgba(255,250,241,0.98) 0%, rgba(255,255,255,0.96) 100%)",
                  border: "1px solid rgba(9,33,58,0.08)",
                  boxShadow: "0 30px 80px rgba(9,33,58,0.12)",
                  animation: "riseIn 780ms ease both"
                }}
              >
                <Stack spacing={2.5}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Stack spacing={0.5}>
                      <Typography variant="overline" sx={{ letterSpacing: "0.16em", color: "var(--landing-accent)" }}>
                        Control Preview
                      </Typography>
                      <Typography variant="h5" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
                        Hành trình thẩm định trong một màn hình
                      </Typography>
                    </Stack>
                    <Box
                      sx={{
                        width: 12,
                        height: 12,
                        borderRadius: "50%",
                        bgcolor: "var(--landing-accent-2)",
                        animation: "pulseDot 1.8s ease-out infinite"
                      }}
                    />
                  </Stack>

                  <Paper
                    sx={{
                      p: 2.25,
                      borderRadius: 4,
                      bgcolor: alpha("#b85c38", 0.08),
                      border: `1px solid ${alpha("#b85c38", 0.16)}`
                    }}
                  >
                    <Stack spacing={1.25}>
                      <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography fontWeight={700}>DSS recommendation</Typography>
                        <Chip label="APPROVE_RECOMMENDED" size="small" sx={{ bgcolor: "#fff", fontWeight: 700 }} />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        Credit score 792, risk rank A, projected DTI 31.4%, hồ sơ đủ điều kiện vào vòng phê duyệt nhanh.
                      </Typography>
                    </Stack>
                  </Paper>

                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Paper
                        sx={{
                          p: 2.25,
                          height: "100%",
                          borderRadius: 4,
                          backgroundColor: alpha("#0f766e", 0.08),
                          animation: "floatDrift 5.5s ease-in-out infinite"
                        }}
                      >
                        <Stack spacing={1.25}>
                          <Typography variant="body2" color="text.secondary">
                            Verification
                          </Typography>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <ShieldIcon sx={{ color: "var(--landing-accent-2)" }} />
                            <Typography variant="h6" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
                              KYC / AML / Fraud
                            </Typography>
                          </Stack>
                          <Typography variant="body2">
                            KYC passed, AML passed, fraud flag clear.
                          </Typography>
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Paper
                        sx={{
                          p: 2.25,
                          height: "100%",
                          borderRadius: 4,
                          backgroundColor: alpha("#0f4c81", 0.08),
                          animation: "floatDrift 6.3s ease-in-out infinite 400ms"
                        }}
                      >
                        <Stack spacing={1.25}>
                          <Typography variant="body2" color="text.secondary">
                            Contract & repayment
                          </Typography>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <PaymentIcon sx={{ color: "var(--landing-accent-3)" }} />
                            <Typography variant="h6" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
                              EMI tự động
                            </Typography>
                          </Stack>
                          <Typography variant="body2">
                            Hợp đồng được sinh sau khi phê duyệt và theo dõi dư nợ xuyên suốt vòng đời khoản vay.
                          </Typography>
                        </Stack>
                      </Paper>
                    </Grid>
                  </Grid>

                  <Divider />

                  <Stack spacing={1.5}>
                    {[
                      "Khách hàng khai báo thông tin tài chính và khoản nợ.",
                      "Nhân viên thẩm định truy cập hồ sơ tập trung theo hàng đợi.",
                      "Audit log lưu lại các hành động quan trọng để đối soát."
                    ].map((text) => (
                      <Stack key={text} direction="row" spacing={1.25} alignItems="flex-start">
                        <CheckIcon sx={{ mt: 0.2, color: "var(--landing-accent)" }} fontSize="small" />
                        <Typography variant="body2" color="text.secondary">
                          {text}
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </Stack>
              </Paper>

              <Box
                sx={{
                  position: "absolute",
                  right: { xs: 8, md: -12 },
                  bottom: { xs: 12, md: 44 },
                  width: { xs: 130, md: 170 },
                  p: 2,
                  borderRadius: 4,
                  bgcolor: "#fff",
                  border: "1px solid rgba(9,33,58,0.08)",
                  boxShadow: "0 20px 40px rgba(9,33,58,0.10)",
                  animation: "floatDrift 6s ease-in-out infinite"
                }}
              >
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Audit trail
                  </Typography>
                  <Typography variant="h5" sx={{ fontFamily: "var(--landing-display)", fontWeight: 800 }}>
                    100%
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Các bước phê duyệt quan trọng đều có log.
                  </Typography>
                </Stack>
              </Box>
            </Box>
          </Grid>
        </Grid>

        <Box id="gia-tri" sx={{ pt: { xs: 8, md: 11 } }}>
          <SectionHeading
            eyebrow="Điểm nhấn"
            title="Trang chủ không chỉ là màn hình chờ, mà là bản đồ nghiệp vụ trước khi đăng nhập."
            description="Thiết kế mới biến điểm vào hệ thống thành một landing page rõ bối cảnh, giúp người dùng biết họ sẽ làm gì trước khi xác thực."
            align="center"
          />

          <Grid container spacing={2.5} sx={{ mt: 2 }}>
            {highlights.map((item, index) => (
              <Grid item xs={12} md={4} key={item.title}>
                <HighlightCard {...item} index={index} />
              </Grid>
            ))}
          </Grid>
        </Box>

        <Box id="quy-trinh" sx={{ pt: { xs: 8, md: 11 } }}>
          <SectionHeading
            eyebrow="Quy trình"
            title="Luồng xét duyệt được trình bày theo đúng cách hệ thống đang vận hành."
            description="Từ nộp hồ sơ, chấm điểm DSS, xác minh, ra quyết định đến hợp đồng và thanh toán, landing page cho thấy toàn bộ hành trình chỉ trong vài khối nội dung."
          />

          <Grid container spacing={2.5} sx={{ mt: 2 }}>
            {processSteps.map((item) => (
              <Grid item xs={12} sm={6} lg={3} key={item.step}>
                <ProcessCard {...item} />
              </Grid>
            ))}
          </Grid>
        </Box>

        <Box id="vai-tro" sx={{ pt: { xs: 8, md: 11 } }}>
          <SectionHeading
            eyebrow="Vai trò"
            title="Ba vai trò, ba góc nhìn, một điểm bắt đầu chung."
            description="Người dùng chưa cần đăng nhập để hiểu chính xác họ sẽ vào đâu và thao tác gì sau khi xác thực."
          />

          <Grid container spacing={2.5} sx={{ mt: 2 }}>
            {roleCards.map((card) => (
              <Grid item xs={12} md={4} key={card.role}>
                <Paper
                  sx={{
                    p: 3,
                    height: "100%",
                    borderRadius: 4,
                    border: `1px solid ${alpha(card.color, 0.18)}`,
                    background: `linear-gradient(180deg, ${alpha(card.color, 0.08)} 0%, rgba(255,255,255,0.96) 70%)`
                  }}
                >
                  <Stack spacing={2}>
                    <Chip
                      label={labelRole(card.role)}
                      sx={{
                        width: "fit-content",
                        fontWeight: 800,
                        bgcolor: alpha(card.color, 0.14),
                        color: card.color
                      }}
                    />
                    <Typography variant="h5" sx={{ fontFamily: "var(--landing-display)", fontWeight: 700 }}>
                      {card.title}
                    </Typography>
                    <Stack spacing={1.25}>
                      {card.items.map((item) => (
                        <Stack key={item} direction="row" spacing={1.25} alignItems="flex-start">
                          <CheckIcon sx={{ mt: 0.2, color: card.color }} fontSize="small" />
                          <Typography color="text.secondary">{item}</Typography>
                        </Stack>
                      ))}
                    </Stack>
                  </Stack>
                </Paper>
              </Grid>
            ))}
          </Grid>
        </Box>

        <Paper
          sx={{
            mt: { xs: 8, md: 11 },
            p: { xs: 3, md: 4 },
            borderRadius: 6,
            bgcolor: "var(--landing-ink)",
            color: "#fff",
            overflow: "hidden",
            position: "relative"
          }}
        >
          <Box
            sx={{
              position: "absolute",
              top: -50,
              right: -40,
              width: 220,
              height: 220,
              borderRadius: "50%",
              bgcolor: alpha("#fff", 0.06)
            }}
          />
          <Stack
            direction={{ xs: "column", md: "row" }}
            spacing={3}
            alignItems={{ xs: "flex-start", md: "center" }}
            justifyContent="space-between"
            sx={{ position: "relative" }}
          >
            <Stack spacing={1}>
              <Typography variant="overline" sx={{ letterSpacing: "0.16em", color: alpha("#fff", 0.68) }}>
                Sẵn sàng bắt đầu
              </Typography>
              <Typography sx={{ fontFamily: "var(--landing-display)", fontSize: { xs: "1.9rem", md: "2.6rem" }, fontWeight: 700 }}>
                Vào hệ thống từ đây, nhưng không buộc người dùng đăng nhập ngay khi vừa mở trang.
              </Typography>
              <Typography sx={{ color: alpha("#fff", 0.72), maxWidth: 680 }}>
                CTA được đặt rõ ràng sau khi người dùng đã hiểu mục tiêu của nền tảng, thay vì bị đẩy thẳng vào form xác thực.
              </Typography>
            </Stack>

            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
              <Button
                component={RouterLink}
                to={primaryActionTo}
                variant="contained"
                size="large"
                endIcon={<ArrowIcon />}
                sx={{
                  px: 3,
                  py: 1.35,
                  borderRadius: 999,
                  bgcolor: "#fff",
                  color: "var(--landing-ink)",
                  "&:hover": {
                    bgcolor: "#f4efe6"
                  }
                }}
              >
                {primaryActionLabel}
              </Button>
              <Button
                component="a"
                href="#gia-tri"
                variant="outlined"
                size="large"
                sx={{
                  px: 3,
                  py: 1.35,
                  borderRadius: 999,
                  borderColor: alpha("#fff", 0.28),
                  color: "#fff"
                }}
              >
                Xem lại điểm nổi bật
              </Button>
            </Stack>
          </Stack>
        </Paper>

        <Stack
          direction={{ xs: "column", md: "row" }}
          justifyContent="space-between"
          spacing={2}
          sx={{
            py: 4,
            color: alpha("#09213a", 0.66)
          }}
        >
          <Typography variant="body2">
            Web-based Loan Approval Decision Support System
          </Typography>
          <Typography variant="body2">
            Public landing page trước bước xác thực
          </Typography>
        </Stack>
      </Container>
    </Box>
  );
}
