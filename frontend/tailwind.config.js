/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#0a0a0f',
          900: '#12121a',
          800: '#1a1a24',
          700: '#24242f',
          600: '#32323f',
        },
        accent: {
          DEFAULT: '#6d5dfc',
          light: '#8b7dff',
        },
      },
    },
  },
  plugins: [],
};
