/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // sketchbook palette: paper, ink, and one highlighter accent
        paper: {
          DEFAULT: '#fbf8f1',
          deep: '#f2ecdf',
          line: '#e6dfcf',
        },
        ink: {
          DEFAULT: '#1f1d1a',
          soft: '#57534e',
          faint: '#8a847b',
        },
        marker: {
          DEFAULT: '#ffd23f',
          deep: '#f5b700',
          soft: '#fff1b8',
        },
        go: '#2f9e44',
        stop: '#e03131',
      },
      fontFamily: {
        hand: ['"Patrick Hand"', 'ui-rounded', 'system-ui', 'sans-serif'],
        sans: ['Nunito', 'ui-rounded', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        sketch: '4px 4px 0 0 #1f1d1a',
        'sketch-sm': '2px 2px 0 0 #1f1d1a',
        'sketch-lg': '6px 6px 0 0 #1f1d1a',
      },
    },
  },
  plugins: [],
};
