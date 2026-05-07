import {
  Avatar,
  Badge,
  Box,
  Button,
  ButtonBase,
  CircularProgress,
  Divider,
  IconButton,
  Menu,
  Stack,
  Tooltip,
  Typography
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import AssignmentTurnedInRoundedIcon from "@mui/icons-material/AssignmentTurnedInRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import EventAvailableRoundedIcon from "@mui/icons-material/EventAvailableRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import NotificationsRoundedIcon from "@mui/icons-material/NotificationsRounded";
import PaymentRoundedIcon from "@mui/icons-material/PaymentRounded";
import TaskAltRoundedIcon from "@mui/icons-material/TaskAltRounded";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import {
  getNotificationsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi
} from "@/shared/api/notificationApi";

const EMPTY_FEED = {
  items: [],
  unreadCount: 0
};

const FILTERS = [
  { value: "all", label: "Tất cả" },
  { value: "unread", label: "Chưa đọc" }
];

function formatRelativeTime(value) {
  if (!value) {
    return "";
  }

  const now = Date.now();
  const target = new Date(value).getTime();
  const diffMinutes = Math.max(0, Math.round((now - target) / 60000));
  if (diffMinutes < 1) {
    return "Vừa xong";
  }
  if (diffMinutes < 60) {
    return `${diffMinutes} phút`;
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours} giờ`;
  }

  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 7) {
    return `${diffDays} ngày`;
  }

  const diffWeeks = Math.round(diffDays / 7);
  return `${diffWeeks} tuần`;
}

function isToday(value) {
  if (!value) {
    return false;
  }

  const date = new Date(value);
  const now = new Date();
  return date.toDateString() === now.toDateString();
}

function groupNotifications(items) {
  return {
    today: items.filter((item) => isToday(item.createdAt)),
    earlier: items.filter((item) => !isToday(item.createdAt))
  };
}

function typeConfig(type) {
  switch (type) {
    case "INFORMATION_REVIEW_SUBMITTED":
      return {
        icon: <FactCheckRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#1877f2"
      };
    case "INFORMATION_REVIEW_COMPLETED":
      return {
        icon: <TaskAltRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#2e7d32"
      };
    case "LOAN_APPLICATION_SUBMITTED":
      return {
        icon: <DescriptionRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#7b1fa2"
      };
    case "LOAN_DECISION_UPDATED":
      return {
        icon: <TaskAltRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#1565c0"
      };
    case "APPOINTMENT_SCHEDULED":
      return {
        icon: <EventAvailableRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#ef6c00"
      };
    case "CONTRACT_CREATED":
      return {
        icon: <AssignmentTurnedInRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#00897b"
      };
    case "PAYMENT_DUE_SOON":
      return {
        icon: <PaymentRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#d32f2f"
      };
    default:
      return {
        icon: <NotificationsRoundedIcon sx={{ fontSize: 14 }} />,
        color: "#5f6368"
      };
  }
}

function actorInitial(actorEmail, title) {
  const source = actorEmail || title || "N";
  return source.trim().charAt(0).toUpperCase();
}

function NotificationItem({ item, onClick }) {
  const config = typeConfig(item.type);

  return (
    <ButtonBase
      onClick={() => onClick(item)}
      sx={{
        width: "100%",
        textAlign: "left",
        alignItems: "stretch",
        px: 2,
        py: 1.25,
        justifyContent: "flex-start",
        bgcolor: item.read ? "transparent" : alpha("#1877f2", 0.06),
        "&:hover": {
          bgcolor: item.read ? alpha("#1f4b99", 0.06) : alpha("#1877f2", 0.12)
        }
      }}
    >
      <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ width: "100%" }}>
        <Box sx={{ position: "relative", flexShrink: 0 }}>
          <Avatar
            sx={{
              width: 56,
              height: 56,
              bgcolor: alpha(config.color, 0.18),
              color: config.color,
              fontWeight: 800
            }}
          >
            {actorInitial(item.actorEmail, item.title)}
          </Avatar>
          <Box
            sx={{
              position: "absolute",
              right: -2,
              bottom: -2,
              width: 24,
              height: 24,
              borderRadius: "50%",
              bgcolor: config.color,
              color: "#fff",
              display: "grid",
              placeItems: "center",
              border: "2px solid #fff"
            }}
          >
            {config.icon}
          </Box>
        </Box>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            variant="body1"
            sx={{
              fontWeight: item.read ? 600 : 700,
              lineHeight: 1.35,
              color: "text.primary"
            }}
          >
            {item.actorEmail ? `${item.actorEmail}: ` : ""}
            {item.title}
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              mt: 0.25,
              display: "-webkit-box",
              overflow: "hidden",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical"
            }}
          >
            {item.message}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              mt: 0.75,
              display: "inline-block",
              fontWeight: 700,
              color: item.read ? "text.secondary" : "primary.main"
            }}
          >
            {formatRelativeTime(item.createdAt)}
          </Typography>
        </Box>

        {!item.read && (
          <Box
            sx={{
              width: 12,
              height: 12,
              borderRadius: "50%",
              bgcolor: "primary.main",
              mt: 2,
              flexShrink: 0
            }}
          />
        )}
      </Stack>
    </ButtonBase>
  );
}

function NotificationSection({ title, items, onItemClick }) {
  if (!items.length) {
    return null;
  }

  return (
    <Box>
      <Typography
        variant="subtitle1"
        sx={{
          px: 2,
          pt: 1.75,
          pb: 0.5,
          fontWeight: 800
        }}
      >
        {title}
      </Typography>
      <Stack spacing={0.25}>
        {items.map((item) => (
          <NotificationItem key={item.id} item={item} onClick={onItemClick} />
        ))}
      </Stack>
    </Box>
  );
}

export default function NotificationMenu() {
  const navigate = useNavigate();
  const { accessToken, user } = useAuth();
  const [anchorEl, setAnchorEl] = useState(null);
  const [filter, setFilter] = useState("all");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [feed, setFeed] = useState(EMPTY_FEED);

  const open = Boolean(anchorEl);
  const supportedRole = user?.role === "CUSTOMER" || user?.role === "STAFF";

  const refreshFeed = async ({ silent = false } = {}) => {
    if (!accessToken || !supportedRole) {
      setFeed(EMPTY_FEED);
      return;
    }

    if (!silent) {
      setLoading(true);
      setError("");
    }

    try {
      const response = await getNotificationsApi(accessToken, 20);
      setFeed({
        items: Array.isArray(response?.items) ? response.items : [],
        unreadCount: Number(response?.unreadCount || 0)
      });
    } catch (err) {
      if (!silent) {
        setError(err.message || "Không tải được thông báo");
      }
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    let active = true;
    let intervalId;

    async function bootstrap() {
      if (!active || !accessToken || !supportedRole) {
        if (active) {
          setFeed(EMPTY_FEED);
        }
        return;
      }

      try {
        const response = await getNotificationsApi(accessToken, 20);
        if (!active) {
          return;
        }
        setFeed({
          items: Array.isArray(response?.items) ? response.items : [],
          unreadCount: Number(response?.unreadCount || 0)
        });
      } catch {
        if (active) {
          setFeed(EMPTY_FEED);
        }
      }
    }

    bootstrap();
    if (accessToken && supportedRole) {
      intervalId = window.setInterval(() => {
        bootstrap();
      }, 30000);
    }

    return () => {
      active = false;
      if (intervalId) {
        window.clearInterval(intervalId);
      }
    };
  }, [accessToken, supportedRole, user?.id]);

  const visibleItems = useMemo(() => {
    if (filter === "unread") {
      return feed.items.filter((item) => !item.read);
    }
    return feed.items;
  }, [feed.items, filter]);

  const grouped = useMemo(() => groupNotifications(visibleItems), [visibleItems]);
  const unreadBadge = feed.unreadCount > 99 ? "99+" : feed.unreadCount;

  if (!supportedRole) {
    return null;
  }

  const handleOpen = async (event) => {
    setAnchorEl(event.currentTarget);
    await refreshFeed();
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsReadApi(accessToken);
      setFeed((prev) => ({
        unreadCount: 0,
        items: prev.items.map((item) => ({ ...item, read: true }))
      }));
    } catch (err) {
      setError(err.message || "Không cập nhật được trạng thái thông báo");
    }
  };

  const handleItemClick = async (item) => {
    if (!item.read) {
      try {
        await markNotificationReadApi(accessToken, item.id);
        setFeed((prev) => ({
          unreadCount: Math.max(0, prev.unreadCount - 1),
          items: prev.items.map((entry) => (
            entry.id === item.id ? { ...entry, read: true } : entry
          ))
        }));
      } catch (err) {
        setError(err.message || "Không cập nhật được trạng thái thông báo");
      }
    }

    handleClose();
    if (item.link) {
      navigate(item.link);
    }
  };

  return (
    <>
      <Tooltip title="Thông báo">
        <IconButton color="inherit" onClick={handleOpen}>
          <Badge badgeContent={unreadBadge} color="error">
            <NotificationsRoundedIcon />
          </Badge>
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        MenuListProps={{ disablePadding: true }}
        PaperProps={{
          sx: {
            width: 388,
            maxWidth: "calc(100vw - 24px)",
            borderRadius: 3,
            mt: 1.25,
            overflow: "hidden",
            boxShadow: "0 22px 48px rgba(15, 23, 42, 0.22)"
          }
        }}
      >
        <Box sx={{ px: 2.5, pt: 2.25, pb: 1.5, bgcolor: "#f7f9fc" }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
            <Typography variant="h5" sx={{ fontSize: 30, fontWeight: 800 }}>
              Thông báo
            </Typography>
            <Button
              size="small"
              onClick={handleMarkAllRead}
              disabled={feed.unreadCount === 0}
              sx={{ textTransform: "none", fontWeight: 700 }}
            >
              Đánh dấu đã đọc
            </Button>
          </Stack>

          <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
            {FILTERS.map((item) => (
              <Button
                key={item.value}
                size="small"
                onClick={() => setFilter(item.value)}
                sx={{
                  minWidth: 0,
                  px: 1.8,
                  py: 0.85,
                  borderRadius: 999,
                  textTransform: "none",
                  fontWeight: 800,
                  bgcolor: filter === item.value ? "primary.main" : "rgba(15, 23, 42, 0.06)",
                  color: filter === item.value ? "#fff" : "text.primary",
                  "&:hover": {
                    bgcolor: filter === item.value ? "primary.dark" : "rgba(15, 23, 42, 0.12)"
                  }
                }}
              >
                {item.label}
              </Button>
            ))}
          </Stack>
        </Box>

        <Divider />

        <Box sx={{ maxHeight: 500, overflowY: "auto", bgcolor: "background.paper" }}>
          {loading && !feed.items.length ? (
            <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
              <CircularProgress size={28} />
            </Stack>
          ) : error ? (
            <Box sx={{ px: 2.5, py: 3 }}>
              <Typography color="error" variant="body2">
                {error}
              </Typography>
            </Box>
          ) : visibleItems.length === 0 ? (
            <Box sx={{ px: 2.5, py: 4 }}>
              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                {filter === "unread" ? "Không có thông báo chưa đọc." : "Chưa có thông báo nào."}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                Các cập nhật về hồ sơ vay, lịch hẹn và hợp đồng sẽ xuất hiện ở đây.
              </Typography>
            </Box>
          ) : (
            <>
              <NotificationSection title="Hôm nay" items={grouped.today} onItemClick={handleItemClick} />
              <NotificationSection title="Trước đó" items={grouped.earlier} onItemClick={handleItemClick} />
            </>
          )}
        </Box>
      </Menu>
    </>
  );
}
