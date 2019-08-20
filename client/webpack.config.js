
var HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');

module.exports = {
  entry: __dirname + '/app/index.jsx',
  output: {
    path: __dirname + '/../server/public',
   filename: 'javascripts/bundle.js'
  },
  module: {
    rules: [
      {
        test: /\.(jsx)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              presets: ['@babel/react']
            }
          }
        ],
      },
      {
        test: /\.css$/,
        exclude: /node_modules/,
        use: ['style-loader', 'css-loader']
      },
      {
        test: /\.png$/,
        exclude: /node_modules/,
        use: ['file-loader']
      },
    ]
  },
  resolve: {
    extensions: ['.js', '.jsx', '.css'],
    alias: {
      '@': path.resolve(__dirname, 'app/'),
    },
    modules: [
      'node_modules'
    ]
  },
  plugins: [new HtmlWebpackPlugin({
    template: './app/index.html'
  })],
  externals: {
    // global app config object
    config: JSON.stringify({
      apiUrl: 'http://localhost:4000',
      mailerApiUrl: 'http://localhost:3000'
    })
  }
};
