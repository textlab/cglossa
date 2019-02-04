// Generated by CoffeeScript 1.7.1

/** @jsx React.DOM */

(function() {
  window.TextBox = createReactClass({displayName: "TextBox",
    propTypes: {
      mediaObj: PropTypes.object.isRequired,
      startAtLine: PropTypes.number.isRequired,
      endAtLine: PropTypes.number.isRequired
    },
    getInitialState: function() {
      return {
        wfPlayer: null
      };
    },
    renderWord: function(line, index) {
      var att, attString;
      attString = "";
      for (att in line) {
        if (att === "pos") {
          line[att] = line[att].replace(/:/g, "/");
        }
        attString += att + " : " + line[att] + "<br>";
      }
      return React.createElement("a", {key: index, title: attString, style: line.match ? {color: '#b00', fontWeight: 'bold', fontSize: '0.9em'} : {}}, line[this.props.mediaObj.displayAttribute], " ");
    },
    renderAnnotation: function(annotation, lineNo) {
      var endTimecode, getStyle, i, segment, speaker, textDivs, timecode;
      timecode = annotation.from;
      endTimecode = annotation.to;
      speaker = annotation.speaker;
      segment = (function() {
        var _results;
        _results = [];
        for (i in annotation.line) {
          _results.push(this.renderWord(annotation.line[i], i));
        }
        return _results;
      }).call(this);
      getStyle = (function(_this) {
        return function() {
          if (parseInt(lineNo) === parseInt(_this.props.highlightLine)) {
            return {
              display: 'table-row',
              color: '#000',
              backgroundColor: '#eea'
            };
          } else if (lineNo >= _this.props.startAtLine && lineNo <= _this.props.endAtLine) {
            return {
              display: 'table-row',
              color: '#000'
            };
          } else if (annotation.from === _this.firstStart || annotation.to === _this.lastEnd) {
            return {
              display: 'table-row',
              color: '#ccc'
            };
          } else {
            return {
              display: 'none'
            };
          }
        };
      })(this);
      textDivs = [];
      innerDivs = [];
      if(typeof(this.props.mediaObj) !== 'undefined') {
          innerDivs.push(
           React.createElement("div", {className: "waveformBtnDiv"}, React.createElement("button", {title: "Show waveform", className: "btn btn-xs btn-default", style: {marginRight: 10}, onClick: this.toggleWFplayer.bind(this, lineNo)}, React.createElement("img", {src: "img/speech/waveform.png", style: {width: 12}})))
          );
      }
      innerDivs.push(
         React.createElement("div", {className: "speakerDiv"}, React.createElement("a", {className: "speaker", title: speaker}, speaker)),
         React.createElement("div", {className: "segmentDiv"}, segment)
      );
      textDivs.push(React.createElement("div", {className: 'textDiv ' + timecode.replace(/\./,"_"),
            id: 'jp-' + lineNo, 
            "data-start_timecode": timecode, 
            "data-end_timecode": endTimecode,
            style: getStyle(),
      }, innerDivs));
      if (this.state.wfPlayer === lineNo) {
        textDivs.push(React.createElement("div", {className: "waveDiv"}, React.createElement(WFplayer, {mediaObj: this.props.mediaObj, startAt: lineNo, endAt: lineNo})));
      }
      return textDivs;
    },
    toggleWFplayer: function(line) {
      if (this.state.wfPlayer !== line) {
        this.setState({
          wfPlayer: line
        });
        return this.props.pauseJPlayer();
      } else {
        return this.setState({
          wfPlayer: null
        });
      }
    },
    render: function() {
      var annotation, annotations, displayAttribute, n;
      displayAttribute = this.props.mediaObj.displayAttribute;
      annotation = this.props.mediaObj.divs.annotation;
      this.firstStart = annotation[this.props.startAtLine].from;
      this.lastEnd = annotation[this.props.endAtLine].to;
      annotations = (function() {
        var _results;
        _results = [];
        for (n in annotation) {
          _results.push(this.renderAnnotation(annotation[n], n));
        }
        return _results;
      }).call(this);
      return React.createElement("div", {className: "jplayer-text autocue"}, annotations);
    }
  });

  window.Jplayer = createReactClass({displayName: "Jplayer",
    propTypes: {
      mediaObj: PropTypes.object,
      mediaType: PropTypes.string,
      hasLocalMedia: PropTypes.bool
    },
    getStartLine: function(mediaObj) {
      var minStart, startAt;
      startAt = parseInt(mediaObj.startAt);
      minStart = parseInt(mediaObj.minStart);
      if (!this.props.ctxLines) {
        return startAt;
      } else if (this.props.ctxLines === 'all') {
        return minStart;
      } else if (startAt - this.props.ctxLines >= minStart) {
        return startAt - this.props.ctxLines;
      } else {
        return minStart;
      }
    },
    getEndLine: function(mediaObj) {
      var endAt, maxEnd;
      endAt = parseInt(mediaObj.endAt);
      maxEnd = parseInt(mediaObj.maxEnd);
      if (!this.props.ctxLines) {
        return endAt;
      } else if (this.props.ctxLines === 'all') {
        return maxEnd;
      } else if (endAt + this.props.ctxLines <= maxEnd) {
        return endAt + this.props.ctxLines;
      } else {
        return maxEnd;
      }
    },
    getStartTime: function(mediaObj) {
      return parseFloat(mediaObj.divs.annotation[this.state.startLine].from);
    },
    getEndTime: function(mediaObj) {
      return parseFloat(mediaObj.divs.annotation[this.state.endLine].to);
    },
    getInitialState: function() {
      return {
        startLine: this.getStartLine(this.props.mediaObj),
        endLine: this.getEndLine(this.props.mediaObj),
        currentLine: this.getStartLine(this.props.mediaObj),
        restart: false
      };
    },
    componentDidMount: function() {
      return this.createPlayer();
    },
    componentWillReceiveProps: function(nextProps) {
      if(!_.isEqual(this.props.mediaObj, nextProps.mediaObj)) {
        this.setState({
          startLine: this.getStartLine(nextProps.mediaObj),
          endLine: this.getEndLine(nextProps.mediaObj),
          currentLine: this.getStartLine(nextProps.mediaObj)
        }, function() {
          this.destroyPlayer();
          this.createPlayer();
        });
      }
    },
    componentDidUpdate: function(prevProps, prevState) {
      if (this.state.restart) {
        return this.restartPlayer();
      }
    },
    componentWillUnmount: function() {
      return this.destroyPlayer();
    },
    pauseJPlayer: function() {
      var $node, $playerNode;
      $node = $(ReactDOM.findDOMNode(this));
      $playerNode = $node.find(".jp-jplayer");
      return $playerNode.jPlayer("pause");
    },
    createPlayer: function() {
      var $node, $playerNode, ext, lastLine, mediaObj, mov, path, supplied;
      $node = $(ReactDOM.findDOMNode(this));
      mediaObj = this.props.mediaObj;
      mov = mediaObj.mov.movieLoc;
      //path = "http://localhost:61054/" + mediaObj.mov.path + "/" + this.props.mediaType + "/";
      path = this.props.hasLocalMedia
          ? "http://localhost/" + mediaObj.mov.path.replace(/^media\//, '') + "/" + this.props.mediaType + "/"
          : "http://www.tekstlab.uio.no/glossa2/" + mediaObj.mov.path + "/" + this.props.mediaType + "/";
      supplied = mediaObj.mov.supplied;
      $("#movietitle").text(mov);
      lastLine = parseInt(mediaObj.lastLine);
      ext = this.props.mediaType === "audio" ? ".mp3" : ".mp4";
      $playerNode = $node.find(".jp-jplayer");
      $playerNode.jPlayer({
        solution: "flash, html",
        ready: (function(_this) {
          return function() {
            $playerNode.jPlayer("setMedia", {
              rtmpv: path + mov + ext,
              m4v: path + mov + ext,
              poster: "img/speech/_6.6-%27T%27_ligo.skev.graa.jpg"
            });
            return $playerNode.jPlayer("play", _this.getStartTime(mediaObj));
          };
        })(this),
        timeupdate: (function(_this) {
          return function(event) {
            var ct;
            ct = event.jPlayer.status.currentTime;
            if (ct > _this.getEndTime(mediaObj)) {
              $playerNode = $node.find(".jp-jplayer");
              $playerNode.jPlayer("play", _this.getStartTime(mediaObj));
              $playerNode.jPlayer("pause");
              return _this.setState({
                currentLine: _this.getStartLine(mediaObj),
                restart: false
              });
            } else if (ct > mediaObj.divs.annotation[_this.state.currentLine].to) {
              return _this.setState({
                currentLine: _this.state.currentLine + 1,
                restart: false
              });
            }
          };
        })(this),
        swfPath: "",
        supplied: supplied,
        solution: 'html, flash',
        preload: 'metadata'
      });
      return $(".slider-range").slider({
        range: true,
        min: 0,
        max: lastLine,
        values: [this.state.startLine, this.state.endLine + 1],
        slide: (function(_this) {
          return function(event, ui) {
            if (ui.values[1] - ui.values[0] < 1) {
              return false;
            }
            $playerNode.jPlayer("stop");
            return _this.setState({
              restart: true,
              currentLine: ui.values[0],
              startLine: ui.values[0],
              endLine: ui.values[1] - 1
            });
          };
        })(this)
      });
    },
    destroyPlayer: function() {
      var $node;
      $node = $(ReactDOM.findDOMNode(this));
      return $node.find(".jp-jplayer").jPlayer('destroy');
    },
    restartPlayer: function() {
      var $node, $playerNode;
      $node = $(ReactDOM.findDOMNode(this));
      $playerNode = $node.find(".jp-jplayer");
      return $playerNode.jPlayer("play", this.getStartTime(this.props.mediaObj));
    },
    render: function() {
      return React.createElement("div", {style: {position: 'relative'}}, 
      React.createElement("div", {style: {float: 'right', width: 480}}, 
      React.createElement("div", {className: "jp-video jp-video-270p", id: "jp_container_1"}, 
         React.createElement("div", {className: "jp-type-single"}, 
             React.createElement("div", {className: "jp-jplayer", style: this.props.mediaType == 'audio' ? {display: 'none'} : {width: 480, height: 270}}, 
                 React.createElement("img", {id: "jp_poster_1", src: "img/speech/_6.6-%27T%27_ligo.skev.graa.jpg", style: {width: 480, height: 270, display: 'none'}}),
                 React.createElement("object", {id: "jp_flash_1", name: "jp_flash_1", data: "swf/Jplayer.swf", type: "application/x-shockwave-flash", width: "1", height: "1", tabIndex: "-1", style: {width: 1, height: 1}},
                     React.createElement("param", {name: "flashvars", value: "jQuery=jQuery&id=jplayer&vol=0.8&muted=false"}), 
                     React.createElement("param", {name: "allowscriptaccess", value: "always"}), 
                     React.createElement("param", {name: "bgcolor", value: "#000000"}), 
                     React.createElement("param", {name: "wmode", value: "opaque"})
                 )
             ), 
             React.createElement("div", {className: "jp-gui"}, 
                 React.createElement("div", {className: "jp-video-play", style: this.props.mediaType == 'audio' ? {display: 'none', visibility: 'hidden'} : {display: 'none'}}, 
                     React.createElement("a", {href: "javascript:;", className: "jp-video-play-icon", tabIndex: "1"}, "play")
                 ), 
                 React.createElement("div", {className: "jp-interface"}, 
                     React.createElement("div", null, " "), 
                     React.createElement("div", {className: "jp-controls-holder"}, 
                         React.createElement("ul", {className: "jp-controls"}, 
                             React.createElement("li", null, React.createElement("a", {href: "javascript:;", className: "jp-play", tabIndex: "1", title: "play", style: {display: 'block'}}, "play")), 
                             React.createElement("li", null, React.createElement("a", {href: "javascript:;", className: "jp-pause", tabIndex: "1", title: "pause", style: {display: 'none'}}, "pause")), 
                             React.createElement("li", null, React.createElement("a", {href: "javascript:;", className: "jp-mute", tabIndex: "1", title: "mute"}, "mute")), 
                             React.createElement("li", null, React.createElement("a", {href: "javascript:;", className: "jp-unmute", tabIndex: "1", title: "unmute", style: {display: 'none'}}, "unmute")), 
                             React.createElement("li", null, React.createElement("a", {href: "javascript:;", className: "jp-volume-max", tabIndex: "1", title: "volume max"}, "volume-max"))
                         ), 
                         React.createElement("div", {className: "jp-volume-bar"}, 
                             React.createElement("div", {className: "jp-volume-bar-value", style: {width: '80%'}})
                         )
                     ), 
                     React.createElement("div", {className: "jp-title"}, React.createElement("ul", null, React.createElement("li", {id: "movietitle"}, "kristiansand_01um-02uk")))
                 )
             ), 
             React.createElement("div", {className: "jp-no-solution", style: {display: 'none'}}, 
                 React.createElement("span", null, "Update required"), React.createElement("a", {href: "http://get.adobe.com/flashplayer/", target: "_blank"}, "Flash plugin")
             )
         )
      ), 
      React.createElement("div", {className: "slider-range ui-slider ui-slider-horizontal ui-widget ui-widget-content ui-corner-all", "aria-disabled": "false"}, 
          React.createElement("div", {className: "ui-slider-range ui-widget-header ui-corner-all", style: {left: '40%', width: '40%'}}), 
          React.createElement("a", {className: "ui-slider-handle ui-state-default ui-corner-all", href: "#", style: {left: '40%'}}), 
          React.createElement("a", {className: "ui-slider-handle ui-state-default ui-corner-all", href: "#", style: {left: '80%'}})
      )
      ), 
      this.props.mediaObj && React.createElement(TextBox, {mediaObj: this.props.mediaObj, startAtLine: this.state.startLine, endAtLine: this.state.endLine, highlightLine: this.state.currentLine, pauseJPlayer: this.pauseJPlayer})
    );
    }
  });

}).call(this);
