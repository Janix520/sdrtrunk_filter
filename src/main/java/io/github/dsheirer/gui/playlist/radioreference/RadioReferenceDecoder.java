/*
 * *****************************************************************************
 *  Copyright (C) 2014-2020 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.gui.playlist.radioreference;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.identifier.talkgroup.LTRTalkgroup;
import io.github.dsheirer.identifier.talkgroup.TalkgroupIdentifier;
import io.github.dsheirer.identifier.talkgroup.UnknownTalkgroupIdentifier;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.mpt1327.identifier.MPT1327Talkgroup;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.module.decode.passport.identifier.PassportTalkgroup;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.rrapi.type.Flavor;
import io.github.dsheirer.rrapi.type.Site;
import io.github.dsheirer.rrapi.type.System;
import io.github.dsheirer.rrapi.type.Tag;
import io.github.dsheirer.rrapi.type.Talkgroup;
import io.github.dsheirer.rrapi.type.Type;
import io.github.dsheirer.rrapi.type.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for decoding type, flavor and voice for systems and formatting talkgroups according to user preferences.
 */
public class RadioReferenceDecoder
{
    private static final Logger mLog = LoggerFactory.getLogger(RadioReferenceDecoder.class);

    private UserPreferences mUserPreferences;
    private Map<Integer,Flavor> mFlavorMap;
    private Map<Integer,Tag> mTagMap;
    private Map<Integer,Type> mTypeMap;
    private Map<Integer,Voice> mVoiceMap;

    /**
     * Constructs an instance
     * @param userPreferences for settings lookup
     * @param typeMap from radio reference
     * @param flavorMap from radio reference
     * @param voiceMap from radio reference
     * @param tagMap from radio reference
     */
    public RadioReferenceDecoder(UserPreferences userPreferences, Map<Integer,Type> typeMap,
                                 Map<Integer,Flavor> flavorMap, Map<Integer,Voice> voiceMap, Map<Integer,Tag> tagMap)
    {
        mUserPreferences = userPreferences;
        mTypeMap = typeMap;
        mFlavorMap = flavorMap;
        mVoiceMap = voiceMap;
        mTagMap = tagMap;
    }

    /**
     * Converts radio reference formatted talkgroup values to sdrtrunk decimal format
     * @param talkgroup to convert
     * @param system to identify the protocol
     * @return sdrtrunk formatted decimal value
     */
    public int getTalkgroupValue(Talkgroup talkgroup, System system)
    {
        Protocol protocol = getProtocol(system);

        switch(protocol)
        {
            case LTR:
                int value = talkgroup.getDecimalValue();
                int area = (value >= 100000 ? 1 : 0);
                int home = ((value % 100000) / 1000);
                int group = (value % 1000);
                return LTRTalkgroup.create(area, home, group);
            case MPT1327:
                int mptValue = talkgroup.getDecimalValue();
                int prefix = (mptValue / 10000);
                int ident = (mptValue % 10000);
                return MPT1327Talkgroup.encode(prefix, ident);
            default:
                return talkgroup.getDecimalValue();
        }
    }

    /**
     * Creates a talkgroup identifier for the talkgroup using the protocol for the system
     * @param talkgroup with value
     * @param system with protocol
     * @return identifier
     */
    public TalkgroupIdentifier getIdentifier(Talkgroup talkgroup, System system)
    {
        Protocol protocol = getProtocol(system);
        int value = getTalkgroupValue(talkgroup, system);

        switch(protocol)
        {
            case APCO25:
                return APCO25Talkgroup.create(value);
            case LTR:
                return LTRTalkgroup.create(value);
            case MPT1327:
                return MPT1327Talkgroup.createTo(value);
            case PASSPORT:
                return PassportTalkgroup.create(value);
            default:
                return UnknownTalkgroupIdentifier.create(value);
        }
    }

    /**
     * Creates a talkgroup alias identifier for the specified radio reference talkgroup and system protocol
     * @param talkgroup to alias
     * @param system to identify the protocol
     * @return aliased talkgroup
     */
    public io.github.dsheirer.alias.id.talkgroup.Talkgroup getTalkgroupAliasId(Talkgroup talkgroup, System system)
    {
        Protocol protocol = getProtocol(system);
        int value = getTalkgroupValue(talkgroup, system);
        return new io.github.dsheirer.alias.id.talkgroup.Talkgroup(protocol, value);
    }

    /**
     * Creates an alias for the specified radio reference talkgroup and a talkgroup alias id for the value.
     * @param talkgroup to alias
     * @param system to identify the protocol
     * @param aliasList for the alias
     * @param group for the alias (optional null)
     * @return alias
     */
    public Alias createAlias(Talkgroup talkgroup, System system, String aliasList, String group)
    {
        Alias alias = new Alias(talkgroup.getAlphaTag());
        alias.setAliasListName(aliasList);
        alias.setGroup(group);
        alias.addAliasID(getTalkgroupAliasId(talkgroup, system));
        return alias;
    }

    /**
     * Formats the talkgroup value according to the system's protocol and user preferences
     */
    public String format(Talkgroup talkgroup, System system)
    {
        return mUserPreferences.getTalkgroupFormatPreference().format(getIdentifier(talkgroup, system));
    }

    /**
     * Looks up the tags for the talkgroup.
     *
     * Note: even though the talkgroup has an array of tags, each tag only has a tag value and not a tag description,
     * so we have to replace the tag with a lookup tag.
     * @param talkgroup that optionally contains a tags array
     * @return tags identified for the talkgroup
     */
    public List<Tag> getTags(Talkgroup talkgroup)
    {
        List<Tag> tags = new ArrayList<>();

        if(talkgroup.getTags() != null)
        {
            for(Tag tag: talkgroup.getTags())
            {
                tags.add(mTagMap.get(tag.getTagId()));
            }
        }

        return tags;
    }

    /**
     * Provides the radio reference Type for the system
     */
    public Type getType(System system)
    {
        return mTypeMap.get(system.getTypeId());
    }

    /**
     * Provides the radio reference Flavor for the system
     */
    public Flavor getFlavor(System system)
    {
        return mFlavorMap.get(system.getFlavorId());
    }

    /**
     * Provides the radio reference voice/traffic channel protocol for the system
     */
    public Voice getVoice(System system)
    {
        return mVoiceMap.get(system.getVoiceId());
    }

    /**
     * Indicates if the system has a protocol supported by sdrtrunk
     */
    public boolean hasSupportedProtocol(System system)
    {
        return getProtocol(system) != Protocol.UNKNOWN;
    }

    /**
     * Indicates if this is an LTR (standard, net or passport) system
     */
    public boolean isLTR(System system)
    {
        return getType(system) != null && getType(system).getName().toLowerCase().contentEquals("ltr");
    }

    /**
     * Indicates if the system is a hybrid Motorola system using a legacy Type II control channel with
     * APCO25 CAI voice channels.
     */
    public boolean isHybridMotorolaP25(System system)
    {
        return getType(system) != null && getType(system).getName().contentEquals("Motorola") &&
            getProtocol(system) == Protocol.APCO25;
    }

    /**
     * Indicates if the site is a simulcast site.
     * @param site to inspect
     * @return true if the site employs LSM modulation
     */
    public boolean isLSM(Site site)
    {
        return site != null && site.getModulation() != null && site.getModulation().contentEquals("LSM");
    }

    /**
     * Identifies the sdrtrunk protocol used by the system.
     * @param system to identify
     * @return protocol or UNKNOWN if the protocol is not supported by sdrtrunk.
     */
    public Protocol getProtocol(System system)
    {
        if(system == null)
        {
            return Protocol.UNKNOWN;
        }

        Type type = getType(system);
        Flavor flavor = getFlavor(system);
        Voice voice = getVoice(system);

        switch(type.getName())
        {
            case "LTR":
                if(flavor.getName().contentEquals("Standard") || flavor.getName().contentEquals("Net"))
                {
                    return Protocol.LTR;
                }
                else if(flavor.getName().contentEquals("Passport"))
                {
                    return Protocol.PASSPORT;
                }
                break;
            case "MPT-1327":
                return Protocol.MPT1327;
            case "Project 25":
                return Protocol.APCO25;
            case "Motorola":
                if(voice.getName().contentEquals("Analog and APCO-25 Common Air Interface") ||
                    voice.getName().contentEquals("APCO-25 Common Air Interface Exclusive"))
                {
                    return Protocol.APCO25;
                }
                break;
            case "DMR":
            case "NXDN":
            case "EDACS":
            case "TETRA":
            case "Midland CMS":
            case "OpenSky":
            case "iDEN":
            case "SmarTrunk":
            case "Other":
            default:
        }

        return Protocol.UNKNOWN;
    }

    /**
     * Decoder type for the specified system, if supported.
     * @param system requiring a decoder type
     * @return decoder type or null.
     */
    public DecoderType getDecoderType(System system)
    {
        Type type = getType(system);
        Flavor flavor = getFlavor(system);
        Voice voice = getVoice(system);

        switch(type.getName())
        {
            case "LTR":
                if(flavor.getName().contentEquals("Net"))
                {
                    return DecoderType.LTR_NET;
                }
                else if(flavor.getName().contentEquals("Passport"))
                {
                    return DecoderType.PASSPORT;
                }
                else
                {
                    return DecoderType.LTR;
                }
            case "MPT-1327":
                return DecoderType.MPT1327;
            case "Project 25":
                if(flavor.getName().contentEquals("Phase II"))
                {
                    return DecoderType.P25_PHASE2;
                }
                else if(flavor.getName().contentEquals("Phase I"))
                {
                    return DecoderType.P25_PHASE1;
                }
                break;
            case "Motorola":
                if(voice.getName().contentEquals("Analog and APCO-25 Common Air Interface") ||
                   voice.getName().contentEquals("APCO-25 Common Air Interface Exclusive"))
                {
                    return DecoderType.P25_PHASE1;
                }
                break;
            case "DMR":
            case "NXDN":

            case "EDACS":
            case "TETRA":
            case "Midland CMS":
            case "OpenSky":
            case "iDEN":
            case "SmarTrunk":
            case "Other":
            default:
        }

        return null;
    }
}
