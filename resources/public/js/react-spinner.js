// https://github.com/chenglou/react-spinner/blob/master/index.js

(function(window, React) {
  var Spinner = React.createClass({displayName: "Spinner",
    render: function() {
      var bars = [];
      var props = this.props;

      for (var i = 0; i < 12; i++) {
        var barStyle = {};
        barStyle.WebkitAnimationDelay = barStyle.animationDelay =
          (i - 12) / 10 + 's';

        barStyle.WebkitTransform = barStyle.transform =
          'rotate(' + (i * 30) + 'deg) translate(146%)';

        bars.push(
          React.createElement("div", {style: barStyle, className: "react-spinner_bar", key: i})
        );
      }

      return (
        React.createElement("div", React.__spread({},  props, {className: (props.className || '') + ' react-spinner'}), 
          bars
        )
      );
    }
  });

  if (typeof module === 'undefined') {
    window.Spinner = Spinner;
  } else {
    module.exports = Spinner;
  }
})((typeof window !== 'undefined' ? window : {}), (typeof require === 'function' ? require('react') : React));
