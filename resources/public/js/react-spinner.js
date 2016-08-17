// https://github.com/chenglou/react-spinner/blob/master/index.js

var _extends = Object.assign || function (target) { for (var i = 1; i < arguments.length; i++) { var source = arguments[i]; for (var key in source) { if (Object.prototype.hasOwnProperty.call(source, key)) { target[key] = source[key]; } } } return target; };

window.Spinner = React.createClass({
  displayName: 'Spinner',

  render: function render() {
    var bars = [];
    var props = this.props;

    for (var i = 0; i < 12; i++) {
      var barStyle = {};
      barStyle.WebkitAnimationDelay = barStyle.animationDelay = (i - 12) / 10 + 's';

      barStyle.WebkitTransform = barStyle.transform = 'rotate(' + i * 30 + 'deg) translate(146%)';

      bars.push(React.createElement('div', { style: barStyle, className: 'react-spinner_bar', key: i }));
    }

    return React.createElement(
      'div',
      _extends({}, props, { className: (props.className || '') + ' react-spinner' }),
      bars
    );
  }
});
