var path = require('path');
var webpack = require('webpack');
var merge = require('webpack-merge');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var ExtractTextPlugin = require('extract-text-webpack-plugin');

var TARGET_ENV = process.env.npm_lifecycle_event === 'build' ? 'production' : 'development';

var commonConfig = {
    devtool: 'source-map',
    output: {
        path: path.join(__dirname, "public"),
        publicPath: "",
        filename: "[hash].js"
    },
    module: {
        rules: [{
            test: /\.js$/,
            enforce: "pre",
            exclude: /node_modules/,
            loader: "source-map-loader"
        }, {
            test: /\.(eot|ttf|woff|woff2|svg|png)$/,
            exclude: /node_modules/,
            loader: 'file-loader'
        }]
    },
    plugins: [
        new HtmlWebpackPlugin({
            template: 'Source/static/index.html',
            inject: 'body',
            filename: 'index.html'
        }),
        new CopyWebpackPlugin([{
            from: 'Source/static/img/',
            to: 'img/'
        }])
    ]
};

if (TARGET_ENV === 'development') {
    console.log('Serving locally...');
    module.exports = merge(commonConfig, {
        entry: [
            './Source/static/index.js',
            'webpack/hot/dev-server',
            'webpack-dev-server/client?http://localhost:8080/',
        ],

        module: {
            rules: [{
                test: /\.(css|scss)$/,
                exclude: /node_modules/,
                use: [
                    'style-loader',
                    'css-loader',
                    'sass-loader'
                ]
            }]
        },

        plugins: [
            // enable HMR globally
            new webpack.HotModuleReplacementPlugin(),

            // print more readable module names in the browser console on HMR updates
            new webpack.NamedModulesPlugin(),
        ],

    });
}

if (TARGET_ENV === 'production') {
    console.log('Building for prod...');

    module.exports = merge(commonConfig, {

        entry: path.join(__dirname, 'Source/static/index.js'),

        module: {
            loaders: [{
                test: /\.(css|scss)$/,
                loader: ExtractTextPlugin.extract({
                    fallbackLoader: 'style-loader',
                    loader: ['css-loader', 'sass-loader']
                })
            }]
        },

        plugins: [
            new CopyWebpackPlugin([{
                from: 'src/static/img/',
                to: 'static/img/'
            }]),

            // extract CSS into a separate file
            new ExtractTextPlugin({
                filename: './[hash].css',
                allChunks: true
            }),

            // minify & mangle JS/CSS
            new webpack.optimize.UglifyJsPlugin({
                sourceMap: true,
                minimize: true,
                compressor: { warnings: false }
                // mangle:  true
            }),

            new webpack.LoaderOptionsPlugin({
                minimize: true
            })
        ]

    });
}