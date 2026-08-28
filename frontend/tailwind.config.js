/**
 * Design tokens for the Restaurant Management System frontend.
 *
 * Palette is deliberately a "kitchen pass at night" register: warm charcoal
 * (not pure black) as the base, an amber/copper accent standing in for the
 * expo pass bell, and the four table/order status colours pinned exactly to
 * what the spec calls out (green/red/blue/amber) so state is never ambiguous
 * across the POS, Kitchen Display, and Manager Dashboard.
 */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#0b0d12",
          900: "#111827",
          800: "#1b2333",
          700: "#242e42",
        },
        accent: {
          DEFAULT: "#f97316",
          soft: "#fdba74",
          dim: "#7c3a10",
        },
        status: {
          available: "#22c55e",
          occupied: "#ef4444",
          billed: "#3b82f6",
          cleaning: "#eab308",
          reserved: "#a855f7",
        },
      },
      fontFamily: {
        display: ["\"Space Grotesk\"", "system-ui", "sans-serif"],
        body: ["Inter", "system-ui", "sans-serif"],
        mono: ["\"JetBrains Mono\"", "ui-monospace", "monospace"],
      },
    },
  },
  plugins: [],
};
