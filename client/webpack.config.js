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
    modules: [
      'node_modules'
    ]
  },
};
